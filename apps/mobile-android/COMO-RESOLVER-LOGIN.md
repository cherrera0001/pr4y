# Cómo resolver el login con Google (Credential Manager)

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

2. **En Google Cloud Console**  
   - APIs y servicios → Credenciales → Cliente OAuth 2.0 de tipo **Android** (o créalo si no existe).  
   - **Nombre del paquete:** debe ser el del APK que compartes:
     - Si es build **prod**: `com.pr4y.app`
     - Si es **dev**: `com.pr4y.app.dev`
   - **Huella digital del certificado SHA-1:** pega la SHA-1 del paso 1.  
   - Puedes tener **varias** huellas en el mismo cliente (por ejemplo debug + release).

3. Guarda. Los cambios pueden tardar unos minutos en aplicarse. Vuelve a probar con el mismo APK.

Si solo tenías registrada la SHA-1 de **debug** (tu PC), el APK **release** que repartes tiene otra SHA-1 y por eso a los usuarios les falla. Añadir la SHA-1 de **release** soluciona el problema.

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
