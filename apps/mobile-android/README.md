# PR4Y – Android (Kotlin + Jetpack Compose)

App móvil offline-first para el cuaderno de oración PR4Y.

## ¿Cómo ver / ejecutar la app?

**Cursor no incluye emulador Android.** Para ver la app tienes estas opciones:

### Desde este IDE (Cursor): crear APK e instalar en el móvil

Sí puedes **generar el APK e instalar en tu teléfono desde la terminal de Cursor**, sin abrir Android Studio para ejecutar:

1. **Requisitos en tu PC**: Android SDK instalado (p. ej. con Android Studio) y `ANDROID_HOME` en el PATH (o que `adb` esté en el PATH).
2. **Teléfono**: modo desarrollador y depuración USB activada, conectado por USB.
3. **En la terminal de Cursor** (desde la raíz del repo):
   - **Solo crear el APK** (queda en `apps/mobile-android/app/build/outputs/apk/debug/`):
     ```bash
     pnpm run mobile:apk
     ```
     o entrando en la carpeta: `cd apps/mobile-android` y luego `.\gradlew.bat assembleDebug` (Windows) / `./gradlew assembleDebug` (Mac/Linux).
   - **Instalar en el dispositivo conectado** (móvil o emulador):
     ```bash
     pnpm run mobile:installDebug
     ```
     o desde `apps/mobile-android`: `.\gradlew.bat installDebug` (Windows) / `./gradlew installDebug` (Mac/Linux).

El proyecto ya incluye el wrapper de Gradle (`gradlew`, `gradlew.bat` y `gradle-wrapper.jar`), así que no hace falta Android Studio para compilar; solo necesitas el SDK (y JDK 17) para que el build funcione.

1. **Recomendado: Android Studio**  
   Abre esta carpeta (`apps/mobile-android`) en Android Studio, deja que sincronice Gradle, crea un AVD (Device Manager) y pulsa **Run 'app'**. Es la forma más directa de ejecutar y depurar.

2. **Desde Cursor (con SDK ya instalado)**  
   - Instala la extensión **Android Emulator Launcher** en Cursor/VS Code (por ejemplo [343max](https://marketplace.visualstudio.com/items?itemName=343max.android-emulator-launcher)).  
   - Necesitas tener Android Studio (o al menos el Android SDK) instalado y `ANDROID_HOME` configurado.  
   - Desde la paleta de comandos (Ctrl+Shift+P): "Launch Android Emulator" y elige un AVD.  
   - En la terminal del proyecto: `gradle wrapper` (si no hay `gradlew`), luego `.\gradlew.bat installDebug` (Windows) o `./gradlew installDebug` (Mac/Linux). La app se instalará en el emulador que esté en marcha.

3. **En tu teléfono (modo desarrollador)**  
   Con el móvil conectado por USB y la depuración USB activada, puedes instalar y ejecutar la app directamente en el dispositivo (sin emulador). Ver la sección *Ejecutar en dispositivo físico* más abajo.

No hay forma de integrar una “suite Android” completa dentro de Cursor: el emulador y el despliegue dependen del SDK de Android, que se gestiona desde Android Studio o desde la línea de comandos con las herramientas del SDK.

## Requisitos

- Android Studio Ladybug (2024.2.1) o superior (o al menos el [Android SDK](https://developer.android.com/studio) instalado)
- JDK 17
- Android SDK 35

### Si el build dice "SDK location not found"

1. Instala **Android Studio** (trae el SDK) desde [developer.android.com/studio](https://developer.android.com/studio), o instala solo el [SDK command-line tools](https://developer.android.com/studio#command-tools).
2. Abre Android Studio → **File** → **Settings** → **Appearance & Behavior** → **System Settings** → **Android SDK**. Copia la ruta que sale en **Android SDK Location**.
3. En `apps/mobile-android` crea el archivo **`local.properties`** (o edita el que haya) con una sola línea:
   ```properties
   sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
   ```
   Sustituye la ruta por la que copiaste (usa `\\` en cada `\` o usa `/` en su lugar).

### Google Sign-In ("No credentials available") y "Error: ID de Google no configurado"

**Consumo separado:** la app Android usa **primero el cliente Android** (package + SHA-1, sin secret); si no está definido, usa el cliente Web como fallback. La web (SaaS) usa solo el cliente Web. El backend (Railway) acepta tokens con audience Web o Android.

| Cliente | Variable (Android) | Uso |
|---------|--------------------|-----|
| **Android** | `GOOGLE_ANDROID_CLIENT_ID` en `local.properties` o desde API `/v1/config` | App Android: `serverClientId` preferido para "Continuar con Google". |
| **Web** | `GOOGLE_WEB_CLIENT_ID` (opcional en app) | Fallback en app si falta Android; versión web (Vercel) y backend. |

Los valores de `local.properties` deberían coincidir con los de Railway para que la app no dependa de `/v1/config` en tiempo de ejecución. Si no hay ningún Client ID en build, la app intenta obtenerlos desde GET `/v1/config`.

**Cómo configurar la app Android:**

1. **Cliente Android** (recomendado para login en la app):
   - En **Google Cloud Console** → Credentials → OAuth 2.0 Client ID de tipo **Android** (no pide secret; sí package + SHA-1).
   - **Package name**: `com.pr4y.app.dev` (flavor dev) y/o `com.pr4y.app` (prod).
   - **SHA-1**: `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android` (Windows).
   - En `local.properties`: `GOOGLE_ANDROID_CLIENT_ID=...` (el mismo valor que en Railway). Opcional: `GOOGLE_WEB_CLIENT_ID` para fallback o para que la app funcione si solo tienes Web configurado.

2. **Ejemplo `local.properties`** (mismos valores que en Railway):
   ```properties
   GOOGLE_ANDROID_CLIENT_ID=123456789-yyy.apps.googleusercontent.com
   GOOGLE_WEB_CLIENT_ID=123456789-zzz.apps.googleusercontent.com
   ```

3. **Vuelve a compilar e instalar** si cambiaste `local.properties`.

Si ves **NoCredentialException**: (a) `GOOGLE_ANDROID_CLIENT_ID` vacío o incorrecto, (b) app no registrada con package + SHA-1 en Google Cloud, o (c) ninguna cuenta de Google en el dispositivo.

### Depurar login con Google (logcat)

Para ver en detalle qué falla al pulsar "Continuar con Google":

1. **Desde PowerShell** en `apps/mobile-android`:
   ```powershell
   .\capture-logcat-login.ps1
   ```
   Cuando pida "Reproduce ahora", pulsa el botón en el móvil. Tras 20 s se guarda un log en `logcat-login-YYYYMMDD-HHmmss.txt`.

2. **Manual**: limpia y captura logcat tú mismo, luego reproduce el fallo:
   ```bash
   adb logcat -c
   adb logcat -v time PR4Y_APP:V PR4Y_ERROR:V PR4Y_NETWORK:V *:E
   ```
   Pulsa "Continuar con Google"; cuando veas el error, detén con Ctrl+C. Busca líneas con `PR4Y_ERROR`, `GetCredentialException`, `Google Auth` o el mensaje/causa de la excepción.

3. La app ya registra en logcat el **tipo de excepción**, **mensaje** y **causa** en cada fallo (por ejemplo `NoCredentialException`, `GetCredentialException` con `type` y `msg`). Revisar ese bloque suele bastar para saber si es configuración (Client ID, SHA-1), cuenta de Google o servidor.

## Build

El repo ya incluye el wrapper de Gradle (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`). Desde la terminal:

```bash
cd apps/mobile-android
.\gradlew.bat assembleDebug   # Windows (o pnpm run mobile:apk desde la raíz)
./gradlew assembleDebug       # Mac/Linux
```

## Ejecutar en emulador

1. Crea un AVD (Android Virtual Device) con API 26+ (por ejemplo **Pixel_7**).
2. **Desconecta el móvil por USB** si no quieres instalar en él (así solo queda el emulador como destino).
3. Inicia el emulador: **Android Studio → Device Manager → elige el AVD (p. ej. Pixel_7) → Run**. Espera a que muestre la pantalla de inicio.
4. En terminal desde `apps/mobile-android`: `.\gradlew.bat installDevDebug` (Windows) o `./gradlew installDevDebug` (Mac/Linux). La app se instalará en el emulador.
5. Opcional: Run > Run 'app' en Android Studio eligiendo el emulador.

La app usa por defecto `http://10.0.2.2:4000/` como API (emulador → localhost del PC).

## Ejecutar en dispositivo físico (modo desarrollador)

Sí puedes instalar y probar la app en tu teléfono, sin emulador:

1. **Activar modo desarrollador** en el móvil: Ajustes → Acerca del teléfono → toca 7 veces en "Número de compilación".
2. **Activar depuración USB**: Ajustes → Opciones de desarrollador → Depuración USB (activar).
3. **Conectar el teléfono por USB** al PC. En el móvil acepta "¿Permitir depuración USB?" y opcionalmente "Permitir siempre desde este equipo".
4. Comprueba que el dispositivo se ve: `adb devices` (necesitas el SDK o Android Studio en el PATH).
5. **Instalar la app**: en Android Studio Run > Run 'app' y elige tu dispositivo, o en terminal desde `apps/mobile-android`: `.\gradlew.bat installDebug` (Windows) / `./gradlew installDebug` (Mac/Linux).

La app se instalará como build de depuración. Para que llegue a tu API en el PC, el teléfono y el PC deben estar en la misma red Wi‑Fi y en la app (o en el build) tendrías que apuntar la URL base a la IP de tu PC (por ejemplo `http://192.168.1.X:4000`); en emulador `10.0.2.2` es el localhost del PC.

## Flujo de la app

1. **Login/Registro**: email + contraseña (mín. 8 caracteres). Tras éxito → pantalla de passphrase.
2. **Passphrase**: primera vez se crea passphrase y se sube la DEK envuelta al servidor; siguientes veces se introduce passphrase para desbloquear.
3. **Home**: lista de pedidos de oración (Room). Botones Diario / Buscar. FAB para nuevo pedido.
4. **Nuevo/Editar pedido**: título + cuerpo. Al guardar se persiste en Room y se añade al outbox cifrado (AES-GCM con DEK) para sync.
5. **Detalle**: ver pedido y botón Editar.
6. **Ajustes**: Sincronizar ahora (push outbox → pull → merge), recordatorio diario (WorkManager), Cerrar sesión.

## Implementado

- Auth: register, login, refresh, logout; tokens en EncryptedSharedPreferences.
- DEK/KEK: PBKDF2 con passphrase, wrap/unwrap DEK, GET/PUT wrapped DEK en API.
- Room: requests, journal, outbox, sync_state.
- Sync: push outbox, pull con cursor, merge en local (last-write-wins); cifrado con DEK.
- Recordatorios: WorkManager periódico (aprox. cada 24 h con delay inicial hasta las 9:00).
- Pantallas: Home, NewEdit, Detail, Journal, Search, Settings, Login, Unlock.

## Estructura

- `data/auth`: AuthTokenStore, AuthRepository
- `data/local`: Room entities y DAOs, AppDatabase
- `data/remote`: ApiService, RetrofitClient
- `data/sync`: SyncRepository
- `crypto`: LocalCrypto (AES-GCM), DekManager (PBKDF2, wrap/unwrap)
- `work`: ReminderWorker, ReminderScheduler
- `ui/screens`: pantallas Compose
- `di`: AppContainer (DB singleton)
