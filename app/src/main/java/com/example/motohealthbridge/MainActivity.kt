package com.example.motohealthbridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var scanButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val seen = linkedMapOf<String, String>()
    private var scanning = false
    private var scanner: BluetoothLeScanner? = null

    companion object {
        private const val REQUEST_PERMISSIONS = 501
        private const val SCAN_DURATION_MS = 15_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        status.text = "Listo. Concedé los permisos y tocá «Iniciar escaneo»."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val title = TextView(this).apply {
            text = "MotoHealthBridge · P0"
            textSize = 24f
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 16, 0, 16)
        }
        scanButton = Button(this).apply {
            text = "Iniciar escaneo (15 s)"
            setOnClickListener { onScanPressed() }
        }
        val clearButton = Button(this).apply {
            text = "Limpiar resultados"
            setOnClickListener {
                seen.clear()
                renderResults()
                status.text = "Resultados limpiados."
            }
        }
        results = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(results, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(title)
        root.addView(status)
        root.addView(scanButton)
        root.addView(clearButton)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPermissions(): Boolean =
        requiredPermissions().all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    private fun onScanPressed() {
        if (scanning) {
            stopScan("Escaneo detenido por el usuario.")
            return
        }
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS)
            return
        }
        startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = manager.adapter
        if (adapter == null) {
            status.text = "Este teléfono no informa un adaptador Bluetooth."
            return
        }
        if (!adapter.isEnabled) {
            status.text = "Activá Bluetooth en Android y volvé a intentarlo."
            return
        }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            status.text = "No se pudo obtener el escáner BLE."
            return
        }

        scanning = true
        scanButton.text = "Detener escaneo"
        status.text = "Escaneando durante 15 segundos… Mantené el reloj cerca."
        seen.clear()
        renderResults()

        try {
            scanner?.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                callback
            )
            handler.postDelayed({
                if (scanning) stopScan("Escaneo finalizado.")
            }, SCAN_DURATION_MS)
        } catch (e: SecurityException) {
            stopScan("Permiso Bluetooth denegado: ${e.message}")
        } catch (e: Exception) {
            stopScan("No se pudo iniciar el escaneo: ${e.message}")
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val advertisedName = result.scanRecord?.deviceName
            val name = try {
                device.name ?: advertisedName ?: "(sin nombre anunciado)"
            } catch (_: SecurityException) {
                advertisedName ?: "(nombre no disponible)"
            }
            val address = try {
                device.address ?: "(dirección no disponible)"
            } catch (_: SecurityException) {
                "(dirección protegida)"
            }
            val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val key = address
            seen[key] = "$name\n  Dirección: $address\n  RSSI: ${result.rssi} dBm\n  Visto: $stamp"
            runOnUiThread { renderResults() }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { stopScan("Falló el escaneo BLE. Código: $errorCode") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan(message: String) {
        if (scanning) {
            try {
                scanner?.stopScan(callback)
            } catch (_: Exception) {
                // Ignore stop errors while cleaning up.
            }
        }
        scanning = false
        scanButton.text = "Iniciar escaneo (15 s)"
        status.text = message
    }

    private fun renderResults() {
        results.text = if (seen.isEmpty()) {
            "Todavía no se detectaron dispositivos."
        } else {
            "Dispositivos detectados: ${seen.size}\n\n" +
                seen.values.joinToString("\n\n--------------------\n\n")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasPermissions()) startScan()
            else status.text = "Faltan permisos. Concedelos desde los permisos de la aplicación."
        }
    }

    override fun onDestroy() {
        if (scanning) stopScan("Actividad cerrada.")
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
