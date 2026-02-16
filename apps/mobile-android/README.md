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

La app **no lee** variables de entorno de Vercel ni Railway en tiempo de ejecución. Los Client IDs se inyectan en **tiempo de compilación** desde `local.properties`. Si falta el **cliente Web**, verás **"Error: ID de Google no configurado"**.

**Importante: hay 2 Client IDs distintos y no hay que confundirlos:**

| Uso | Tipo en Google Cloud | Dónde se usa |
|-----|----------------------|--------------|
| **1. Cliente Web** | OAuth 2.0 → "Web application" | Versión web, backend (Railway/Vercel) y en la app Android como `serverClientId` para obtener el token. |
| **2. Cliente Android** | OAuth 2.0 → "Android" | Solo para la app Android (package name + SHA-1); Google exige este cliente para permitir "Continuar con Google" en el dispositivo. |

**Cómo resolverlo:**

1. **Cliente Web** (obligatorio para que desaparezca el error):
   - Es el **mismo** que usa tu backend en Railway/Vercel. Cópialo desde las variables del backend o desde Google Cloud → Credentials → "Web application".
   - En `local.properties` ponlo en `GOOGLE_WEB_CLIENT_ID=...` (sin comillas).

2. **Cliente Android** (necesario para que el sistema ofrezca la cuenta en el móvil):
   - En **Google Cloud Console** → Credentials → crea un OAuth 2.0 Client ID de tipo **Android** (no Web).
   - **Package name**: `com.pr4y.app.dev` (flavor dev) y/o `com.pr4y.app` (prod).
   - **SHA-1**: `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android` (Windows).
   - Ese Client ID de tipo Android puedes ponerlo en `GOOGLE_ANDROID_CLIENT_ID=` en `local.properties` si quieres (la app lo tiene en BuildConfig); el sistema identifica la app por package + SHA-1.

3. **Ejemplo en `local.properties`** (en `apps/mobile-android`):
   ```properties
   GOOGLE_WEB_CLIENT_ID=123456789-xxx.apps.googleusercontent.com
   GOOGLE_ANDROID_CLIENT_ID=123456789-yyy.apps.googleusercontent.com
   ```
   Sin comillas. El valor de Web es el de tu backend; el de Android es el que creas como tipo "Android".

4. **Vuelve a compilar e instalar** (Gradle solo lee `local.properties` al construir):
   ```bash
   cd apps/mobile-android
   .\gradlew.bat :app:installDevDebug --no-daemon
   ```

Si ves **NoCredentialException: No credentials available**: suele ser (a) `GOOGLE_WEB_CLIENT_ID` vacío o incorrecto, (b) la app Android no registrada con package + SHA-1 correctos, o (c) ninguna cuenta de Google en el dispositivo. Añade una cuenta en Ajustes y vuelve a intentar.

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

1. Crea un AVD (Android Virtual Device) con API 26+.
2. Inicia el emulador.
3. Run > Run 'app' en Android Studio, o: `./gradlew installDebug`.

La app usa por defecto `http://10.0.2.2:4000/` como API (emulador → localhost).

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
