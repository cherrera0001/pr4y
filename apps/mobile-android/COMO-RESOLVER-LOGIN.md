# Cómo resolver el login con Google (Credential Manager)

## Cómo se resuelven las dos firmas (release vs debug)

El código **no** elige ni envía ninguna SHA-1. La app solo usa el **Web Client ID** en `setServerClientId` / `requestIdToken`. Cuando el dispositivo llama a Google para el login, Google recibe la petición del APK y obtiene por su cuenta el **package** y la **firma del certificado** de ese APK. Con eso busca en GCP un cliente Android que coincida (mismo package + misma SHA-1). Si hay uno (release o debug), autoriza y devuelve el token. No hace falta lógica en nuestro código para “elegir” entre firmas: Google lo resuelve según con qué certificado esté firmado el APK instalado.

## Referencia de clientes Android (sin pegar huellas en el repo)

- **Release / Play:** un cliente Android en GCP (p. ej. "pr4y-android-client") con package `com.pr4y.app.dev` y la huella SHA-1 del keystore de release. Esa huella se ve en GCP o con `keytool` sobre tu keystore de producción.
- **Debug:** otro cliente Android (p. ej. "pr4y-android-client-debug") con el mismo package y la huella SHA-1 del debug keystore. Para obtener la de debug sin keytool en PATH: `.\gradlew :app:showDebugSha1` (desde `apps/mobile-android`). No guardes esa salida en el repo.

Variables de entorno y BuildConfig no cambian: mismo Web Client ID; Google asocia cada build al cliente por la firma del APK.

---

## 0. APK compartido con otros usuarios: "No se pudo iniciar sesión con Google"

Si **compartes el APK** (por correo, Drive, etc.) y quienes lo instalan ven el mensaje de error al pulsar "Continuar con Google", la causa habitual es:

**Google no reconoce el certificado con el que está firmado ese APK.**  
El APK que repartes suele ser **release** (o prodRelease), firmado con **tu keystore de release**. La **huella SHA-1 de ese keystore** debe estar registrada en Google Cloud Console en el cliente OAuth de tipo **Android**, con el **nombre del paquete** que usa ese build.

### Qué hacer (desarrollador)

1. **Obtener la SHA-1 del keystore con el que firmas el APK que compartes**  
   Si usas el `signingConfig` "release" de `app/build.gradle.kts` (leyendo `storeFile`, etc. de `local.properties` o variables de entorno):
   ```bash
   keytool -list -v -keystore RUTA_A_TU_KEYSTORE -alias TU_ALIAS
   ```
   (Te pedirá la contraseña del keystore.) Copia la línea **SHA1** (formato `AA:BB:CC:...`).

   **En este proyecto:** el keystore suele ser `keystore-pr4y.jks` (en `app/` o la ruta indicada en `local.properties` como `storeFile`). El `keyAlias` está en `local.properties`. Ejemplo (Windows, ajusta la ruta si tu archivo está en otro sitio):
   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "C:\Users\TU_USUARIO\StudioProjects\pr4y\apps\mobile-android\app\keystore-pr4y.jks" -alias TU_ALIAS_DE_LOCAL_PROPERTIES
   ```
   **Importante:** Si tienes un archivo `sha1_real.txt` en `app/`, comprueba si es la salida del certificado **debug** (`Alias name: androiddebugkey`). Esa SHA-1 no sirve para el APK que compartes; necesitas la SHA-1 del keystore de **release** (keystore-pr4y.jks).

2. **En Google Cloud Console**  
   - APIs y servicios → Credenciales → Cliente OAuth 2.0 de tipo **Android** (o créalo si no existe).  
   - **Nombre del paquete:** debe ser el del APK que compartes:
     - Si es build **prod**: `com.pr4y.app`
     - Si es **dev**: `com.pr4y.app.dev`
   - **Huella digital del certificado SHA-1:** pega la SHA-1 del paso 1.  
   - Puedes tener **varias** huellas en el mismo cliente (por ejemplo debug + release).

3. Guarda. Los cambios pueden tardar unos minutos en aplicarse. Vuelve a probar con el mismo APK.

Si solo tenías registrada la SHA-1 de **debug** (tu PC), el APK **release** que repartes tiene otra SHA-1 y por eso a los usuarios les falla. Añadir la SHA-1 de **release** soluciona el problema.

### Alternativa: firmar los builds de desarrollo con el mismo keystore que Play

Si en **`local.properties`** tienes configurado el keystore de release (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), el proyecto está configurado para que **también los builds debug** (prodDebug, devDebug) se firmen con ese keystore. Así solo necesitas **una** SHA-1 en GCP (la de producción) y el login con Google funciona tanto en desarrollo como en Play. No hace falta registrar la SHA-1 del debug.keystore. Si quieres volver a usar la firma debug por defecto, comenta o borra las propiedades del keystore en `local.properties` (o usa un `local.properties` sin ellas).

---

## 1. Confirmar la causa en el siguiente logcat

Se han añadido logs en `LoginViewModel` para que la próxima captura sea útil:

- **`Login: Intento 1 falló, intentando fallback sin nonce`** → El primer intento (con nonce) falló; se está probando el fallback.
- **`Login: NoCredentialException (Google no reconoce la app). Revisa SHA-1 en GCP.`** → Google no reconoce la combinación **package + certificado**. Es la causa más habitual cuando el flujo se cierra sin elegir cuenta.

**Qué hacer:** Repetir la prueba **sin cerrar** la pantalla de Google (deja que termine eligiendo cuenta o mostrando error). Luego captura el logcat.

**Solo tags de la app (menos ruido):**
```bash
adb logcat -s PR4Y_APP:V PR4Y_ERROR:V
```

**Todo el proceso de la app (incluye Credential Manager, Play Services, etc.):** abre la app, luego en otra terminal:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$appPid = & $adb shell pidof com.pr4y.app.dev
& $adb logcat --pid=$appPid
```
(No uses `$pid`: en PowerShell es variable reservada.)
En **Android Studio**: Logcat → desplegable de filtro → "Show only selected application" (con el dispositivo y la app seleccionados) para ver solo el proceso de tu app.

Si aparece **NoCredentialException**, sigue el paso 2. Si no aparece y ves otro error, usa ese mensaje para buscar la solución.

---

## 2. Resolver NoCredentialException (SHA-1 / package en GCP)

Cuando Google devuelve `NoCredentialException`, suele ser porque la app instalada **no está autorizada** en el cliente Android de tu proyecto de Google Cloud.

### Checklist en Google Cloud Console

1. **APIs y servicios → Credenciales** → Abre el cliente OAuth 2.0 de tipo **Android** (no el de tipo Web).
2. **Nombre del paquete:** el del APK que usas: `com.pr4y.app` (build prod) o `com.pr4y.app.dev` (build dev). Si repartes APK a otros, suele ser `com.pr4y.app`.
3. **Huella digital SHA-1:** debe ser la del certificado con el que firmas el APK que instalas:
   - **Debug / desarrollo:** la del keystore por defecto de Android Studio (p. ej. `~/.android/debug.keystore`). Para verla:
     ```bash
     keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android
     ```
   - **Release / APK para compartir:** la del keystore que configuraste en `app/build.gradle.kts` (signingConfig "release", `storeFile` en `local.properties`). Mismo comando con esa ruta y alias.
   - Copia la línea **SHA1** (formato `AA:BB:CC:...`) y pégala en el campo **Huella digital del certificado SHA-1** del cliente Android en GCP. Puedes tener varias huellas (debug + release) en el mismo cliente.
4. Guarda los cambios. Los cambios pueden tardar unos minutos en propagarse.

### Comprobar en la app

- **`local.properties`** debe tener `GOOGLE_WEB_CLIENT_ID=` con el **Web Client ID** del mismo proyecto (el que termina en `.apps.googleusercontent.com`). No cambies esto si ya está bien; el problema de NoCredentialException se resuelve en la consola con el **cliente Android** (package + SHA-1).

---

## 3. Otros fallos posibles

| Si en el logcat ves… | Acción |
|----------------------|--------|
| **`serverClientId should not be empty`** / **`IllegalArgumentException`** en `setServerClientId` o `LoginViewModel.performAuth` | La app se compiló sin Web Client ID. Añade en `local.properties`: `GOOGLE_WEB_CLIENT_ID=TU_WEB_CLIENT_ID.apps.googleusercontent.com` (prod) o `GOOGLE_WEB_CLIENT_ID_DEV=...` (dev). Recompila e instala. |
| **`Login: legacy failed`** / **`ApiException: 10`** (DEVELOPER_ERROR) tras elegir cuenta en el flujo legacy | Google no reconoce la combinación **package + certificado** del APK. Añade la **huella SHA-1** del certificado con el que firmas este APK en el cliente OAuth de tipo **Android** en GCP (mismo proyecto que el Web Client ID). Package: `com.pr4y.app` (prod) o `com.pr4y.app.dev` (dev). Ver sección 2. |
| **AuthRepository: googleLogin network/DNS error** | Revisar conexión a internet y que el backend (Railway) esté accesible. |
| **AuthRepository: getPublicConfig failed** | Revisar URL del API y que el servidor responda. |
| **Acceso denegado por el búnker** | El backend rechazó el token (p. ej. `GOOGLE_WEB_CLIENT_ID` en Railway distinto al de la app). Revisar que backend y app usen el mismo Web Client ID. |
| Usuario cierra la pantalla de Google sin elegir cuenta | No es un bug; el flujo se cancela. Para probar, hay que elegir una cuenta o esperar el mensaje de error de Google. |

---

## 4. Resumen

1. **Volver a probar** dejando que el flujo de Google termine (elegir cuenta o error) y capturar logcat con `PR4Y_APP` / `PR4Y_ERROR`.
2. Si sale **NoCredentialException** → Añadir o corregir **SHA-1** y **package** en el cliente **Android** de GCP.
3. Mantener **GOOGLE_WEB_CLIENT_ID** en `local.properties` igual al Web Client ID del mismo proyecto en GCP.

Para más detalle sobre SHA-1 vs Web Client ID: `AUDITORIA-IDENTIDAD-EVALUACION.md`.
