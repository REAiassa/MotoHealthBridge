# MotoHealthBridge P0

Prototipo Android mínimo para detectar dispositivos Bluetooth Low Energy (BLE) cerca del teléfono. Esta versión **solo escanea**; no se conecta al reloj, no lee datos de salud y no escribe en Health Connect.

## Requisitos de compilación

- JDK 17
- Android SDK Platform 35 y Build Tools 35.0.0
- Gradle 8.9
- Android 8.0 (API 26) o posterior para ejecutar el prototipo

Windows 7 no es el entorno recomendado para instalar las herramientas Android actuales. Usá GitHub Codespaces o GitHub Actions para compilar.

## Opción A: compilar con GitHub Actions (recomendada)

1. Creá un repositorio nuevo en GitHub, por ejemplo `MotoHealthBridge`.
2. Subí todos los archivos de esta carpeta conservando la estructura.
3. En GitHub, abrí **Actions** y habilitá los workflows si te lo solicita.
4. Abrí el workflow **Build Android APK** y elegí **Run workflow**. También se ejecuta al hacer push a `main`.
5. Cuando termine correctamente, abrí la ejecución y descargá el artefacto `MotoHealthBridge-debug`.
6. Descomprimí el artefacto en el teléfono. Instalá `app-debug.apk`. Es posible que Android pida autorizar la instalación desde el navegador o administrador de archivos que uses.

Si la rama principal de tu repositorio no se llama `main`, cambiá el nombre en `.github/workflows/build-apk.yml`.

## Opción B: compilar desde Codespaces

1. Creá un Codespace para el repositorio.
2. Comprobá las herramientas disponibles:

   ```bash
   java -version
   gradle --version
   echo "$ANDROID_HOME"
   ```

3. El entorno necesita JDK 17, Gradle 8.9 y Android SDK Platform 35/Build Tools 35.0.0. Si falta el SDK, la compilación no va a funcionar hasta instalarlo. En caso de dudas, usá GitHub Actions, que prepara esas herramientas automáticamente.
4. Desde la raíz del repositorio:

   ```bash
   gradle assembleDebug
   ```

5. El APK quedará en:

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

Para descargarlo desde Codespaces podés usar el explorador de archivos de VS Code web y seleccionar **Download** sobre el APK.

## Prueba en el Moto Edge 60

1. Dejá el Moto Watch 120 emparejado con Moto Watch Lite.
2. Instalá y abrí MotoHealthBridge P0.
3. Concedé los permisos Bluetooth cuando Android los solicite.
4. Activá Bluetooth y mantené el reloj cerca.
5. Tocá **Iniciar escaneo (15 s)**.
6. Guardá o copiá el nombre, dirección y RSSI de los dispositivos detectados.

La dirección Bluetooth es un identificador del dispositivo; no publiques el registro completo si preferís mantenerlo privado. El nombre puede no aparecer. Para reconocer el reloj, compará los resultados con una prueba en la que el reloj esté apagado o fuera de alcance, sin desemparejarlo.

## Limitaciones conocidas

- Esta versión no explora GATT ni accede a mediciones.
- No apagues Bluetooth ni desemparejes el reloj para hacer la prueba.
- `neverForLocation` puede filtrar algunos anuncios BLE. Si no se detecta el reloj, el siguiente paso será revisar los permisos y probar una configuración de escaneo alternativa.
- No se garantiza que el reloj exponga datos históricos a aplicaciones de terceros.

## Siguientes etapas

- P0.1: detectar el reloj de forma reproducible.
- P0.2: descubrir servicios y características GATT en modo de solo lectura.
- P1: comprobar si alguna característica permite recuperar mediciones.
- P2: diseñar importación local y sincronización con Health Connect con control de duplicados.
