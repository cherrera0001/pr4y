# Despliegue en dispositivo (Android Studio)

## Estado del build

- **Compilación**: correcta. La tarea `assembleDevDebug` genera el APK sin errores.
- **APK generado**: `app/build/outputs/apk/dev/debug/app-dev-debug.apk`
- **Variante para desarrollo**: **devDebug** (package `com.pr4y.app.dev`).

## Si no puedes compilar o instalar desde Android Studio

### 1. Usar la variante correcta

- Menú **Build → Select Build Variant**.
- En el módulo **app**, elige **devDebug** (no prodDebug ni release).
- Así el botón **Run** compila e instala la app de desarrollo.

### 2. Dispositivo OFFLINE (error al instalar)

Si Gradle o Android Studio dicen que el dispositivo está **OFFLINE**:

1. **Desbloquea el teléfono** y déjalo en la pantalla de inicio.
2. **Desconecta y vuelve a conectar** el cable USB.
3. En el móvil, si sale el aviso **“¿Permitir depuración USB?”**, pulsa **Permitir** (y opcionalmente “Confiar en este equipo”).
4. En Android Studio: **View → Tool Windows → Device Manager** y comprueba que el dispositivo aparece como conectado.
5. Vuelve a **Run** (▶).

### 3. Sincronizar y limpiar

- **File → Sync Project with Gradle Files**.
- Si algo sigue fallando: **Build → Clean Project**, luego **Build → Rebuild Project**.

### 4. Instalación manual del APK (si Run sigue fallando)

1. Genera el APK desde la terminal (en la raíz de `apps/mobile-android`):
   ```bash
   .\gradlew assembleDevDebug
   ```
2. El archivo queda en: `app\build\outputs\apk\dev\debug\app-dev-debug.apk`.
3. Copia ese APK al móvil (por cable, Drive, etc.) y ábrelo en el teléfono para instalarlo.
4. O, con el móvil conectado y **en línea** (no OFFLINE), desde la carpeta del proyecto:
   ```bash
   adb install -r app\build\outputs\apk\dev\debug\app-dev-debug.apk
   ```
   (`adb` suele estar en el SDK de Android que usa Android Studio.)

## Resumen

| Qué quieres hacer      | Acción principal                          |
|------------------------|-------------------------------------------|
| Compilar en Android Studio | Build Variant = **devDebug**, luego Build → Make Project |
| Instalar en el móvil   | Dispositivo **conectado y no OFFLINE**, luego Run |
| Solo tener el APK      | `.\gradlew assembleDevDebug` → APK en `app/build/outputs/apk/dev/debug/` |
