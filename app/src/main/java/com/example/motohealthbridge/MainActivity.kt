package com.example.motohealthbridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var scanButton: Button
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val seen = linkedMapOf<String, String>()
    private var scanning = false
    private var scanner: BluetoothLeScanner? = null

    companion object {
        private const val REQUEST_PERMISSIONS = 501
        private const val SCAN_DURATION_MS = 15_000L
        // Address reported by the user's Bluetooth Scanner test for Moto Watch 120-70DA.
        private const val WATCH_ADDRESS = "78:02:B7:16:70:DA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        status.text = "Listo. El reloj identificado es Moto Watch 120-70DA."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val title = TextView(this).apply {
            text = "MotoHealthBridge · P0.2"
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
        val connectButton = Button(this).apply {
            text = "Conectar al Moto Watch 120 y descubrir GATT"
            setOnClickListener { connectToWatch() }
        }
        val disconnectButton = Button(this).apply {
            text = "Desconectar del reloj"
            setOnClickListener { disconnectGatt("Desconectado por el usuario.") }
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
        root.addView(connectButton)
        root.addView(disconnectButton)
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
                scanCallback
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

    private val scanCallback = object : ScanCallback() {
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
            seen[address] = "$name\n  Dirección: $address\n  RSSI: ${result.rssi} dBm\n  Visto: $stamp"
            runOnUiThread { renderResults() }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { stopScan("Falló el escaneo BLE. Código: $errorCode") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToWatch() {
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS)
            status.text = "Concedé los permisos Bluetooth y volvé a tocar Conectar."
            return
        }
        if (scanning) stopScan("Escaneo detenido antes de conectar.")
        if (gatt != null) {
            status.text = "Ya existe una sesión GATT. Desconectá antes de repetir."
            return
        }
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
            if (adapter == null || !adapter.isEnabled) {
                status.text = "Bluetooth no está disponible o está apagado."
                return
            }
            val device = adapter.getRemoteDevice(WATCH_ADDRESS)
            status.text = "Solicitando conexión BLE con Moto Watch 120-70DA…"
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) status.text = "Android no pudo iniciar la conexión GATT."
        } catch (e: SecurityException) {
            status.text = "Permiso Bluetooth denegado: ${e.message}"
        } catch (e: IllegalArgumentException) {
            status.text = "Dirección Bluetooth inválida: ${e.message}"
        } catch (e: Exception) {
            status.text = "No se pudo iniciar GATT: ${e.message}"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            bluetoothGatt: BluetoothGatt,
            statusCode: Int,
            newState: Int
        ) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    status.text = "Error de conexión GATT: código $statusCode. Puede que Moto Watch Lite tenga ocupada la conexión."
                }
                closeGatt(bluetoothGatt)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runOnUiThread { status.text = "Conectado por BLE. Descubriendo servicios GATT…" }
                    try {
                        bluetoothGatt.discoverServices()
                    } catch (e: SecurityException) {
                        runOnUiThread { status.text = "Sin permiso para descubrir servicios: ${e.message}" }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runOnUiThread { status.text = "Conexión GATT desconectada." }
                    closeGatt(bluetoothGatt)
                }
            }
        }

        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, statusCode: Int) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread { status.text = "Falló el descubrimiento GATT: código $statusCode" }
                return
            }
            val report = StringBuilder()
            report.append("Moto Watch 120-70DA\n")
            report.append("Dirección: $WATCH_ADDRESS\n")
            report.append("Servicios encontrados: ${bluetoothGatt.services.size}\n\n")
            bluetoothGatt.services.forEachIndexed { index, service ->
                report.append("Servicio ${index + 1}: ${service.uuid}\n")
                service.characteristics.forEach { characteristic ->
                    report.append("  Característica: ${characteristic.uuid}\n")
                    report.append("  Propiedades: ${propertyNames(characteristic)}\n")
                }
                report.append("\n")
            }
            runOnUiThread {
                status.text = "Descubrimiento GATT completado. No se leyeron ni modificaron características."
                results.text = report.toString()
            }
        }
    }

    private fun propertyNames(characteristic: BluetoothGattCharacteristic): String {
        val p = characteristic.properties
        val names = mutableListOf<String>()
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) names.add("READ")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) names.add("WRITE")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) names.add("WRITE_NO_RESPONSE")
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) names.add("NOTIFY")
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) names.add("INDICATE")
        if (p and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) names.add("BROADCAST")
        if (p and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) names.add("SIGNED_WRITE")
        return if (names.isEmpty()) "sin propiedades declaradas" else names.joinToString(", ")
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(target: BluetoothGatt) {
        try { target.disconnect() } catch (_: Exception) {}
        try { target.close() } catch (_: Exception) {}
        if (gatt === target) gatt = null
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt(message: String) {
        val current = gatt
        if (current != null) closeGatt(current)
        status.text = message
    }

    @SuppressLint("MissingPermission")
    private fun stopScan(message: String) {
        if (scanning) {
            try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
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
            if (hasPermissions()) {
                status.text = "Permisos concedidos. Elegí escanear o conectar al reloj."
            } else {
                status.text = "Faltan permisos. Concedelos desde los permisos de la aplicación."
            }
        }
    }

    override fun onDestroy() {
        if (scanning) stopScan("Actividad cerrada.")
        gatt?.let { closeGatt(it) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
