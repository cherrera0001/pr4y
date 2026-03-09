# Prompt: Revisión de bug según logcat de login

Copia y pega el siguiente bloque en el otro agente:

---

**Revisión de bug de login (Android) según logcat**

Necesito que revises el fallo de login con Google en la app Android **com.pr4y.app.dev** (pr4y) usando el logcat que capturé.

**Archivo de logcat a analizar:**  
`apps/mobile-android/logcat-login-20260218-170136.txt`

**Qué hacer:**

1. **Lee el archivo** `apps/mobile-android/logcat-login-20260218-170136.txt` (ruta relativa a la raíz del repo).

2. **Identifica la causa del fallo** buscando en el log:
   - Líneas con tag **PR4Y_ERROR**, **PR4Y_APP**, **PR4Y_NETWORK**, **PR4Y_CRYPTO**
   - Excepciones: **NoCredentialException**, **GetCredentialException**, o cualquier `Exception`/`Error`
   - Mensajes de Google Auth, Credential Manager o Play Services relacionados con el login
   - Errores HTTP (401, 503) si aplica

3. **Resume:** qué pasó (ej. “Google no reconoce la app”, “usuario canceló”, “error de red”), en qué momento del flujo y qué línea(s) del log lo demuestran.

4. **Propón correcciones concretas** en el código del proyecto (archivos y cambios) según la causa detectada. Si la causa es configuración (SHA-1, Client ID, paquete en Google Cloud), indícalo y enlaza a la documentación del repo (p. ej. `apps/mobile-android/COMO-RESOLVER-LOGIN.md`).

Contexto: la app usa Credential Manager para “Continuar con Google”; el log se capturó con el script `capture-logcat-login.ps1` (filtros PR4Y_* y *:E).

---

*Generado para usar con el logcat `logcat-login-20260218-170136.txt`.*
