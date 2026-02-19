# Prompt único: evaluar logcat de login y corregir

Copia el bloque siguiente y reemplaza `RUTA_DEL_LOGCAT` por el archivo a analizar (ej.: `apps/mobile-android/logcat-login-20260218-182210.txt`). Pega en el agente y ejecuta.

---

**Evaluación de logcat de login (Android) y corrección**

1. **Lee el archivo de logcat**  
   Ruta: `RUTA_DEL_LOGCAT` (relativa a la raíz del repo).

2. **Identifica la causa del fallo** usando solo evidencia del log:
   - Tags de la app: **PR4Y_ERROR**, **PR4Y_APP**, **PR4Y_NETWORK**, **PR4Y_CRYPTO**, **PR4Y_DEBUG**
   - Excepciones y stack traces: clase, mensaje y línea (ej. `LoginViewModel.kt:75`)
   - Mensajes de Google Auth, Credential Manager, Play Services
   - Errores HTTP (401, 503) si aparecen

3. **Resumen obligatorio** (basado solo en el log):
   - Qué pasó (ej. "serverClientId vacío", "NoCredentialException", "usuario canceló", "error de red")
   - En qué momento del flujo (primer intento, reintento con nonce, legacy)
   - Cita 1–3 líneas exactas del log que lo demuestren

4. **Correcciones en código o configuración:**
   - Si el log muestra **`serverClientId should not be empty`** o **`IllegalArgumentException`** en `GetGoogleIdOption.Builder.setServerClientId`: la app se compiló sin `GOOGLE_WEB_CLIENT_ID`. Corregir: en `LoginViewModel` validar que `BuildConfig.GOOGLE_WEB_CLIENT_ID` no esté vacío antes de llamar a Credential Manager y mostrar un mensaje de error claro al usuario; documentar en `COMO-RESOLVER-LOGIN.md` que hay que definir `GOOGLE_WEB_CLIENT_ID` en `local.properties` (o variable de entorno) para el flavor correspondiente.
   - Si el log muestra **NoCredentialException**: ver `COMO-RESOLVER-LOGIN.md` (SHA-1, cliente Android en GCP, package). No inventar causas sin líneas del log.
   - Si es otro error: proponer cambio concreto (archivo, función, qué añadir o quitar) citando la línea del log que lo justifica.

5. **Aplicar los cambios** en el repo: modificar solo los archivos necesarios, sin tocar código no relacionado. Resolver warnings si los hay.

Contexto: app pr4y (com.pr4y.app / com.pr4y.app.dev), login con Google vía Credential Manager y fallback a Google Sign-In legacy. Logcat capturado con `capture-logcat-login.ps1` (filtros PR4Y_* y *:E).

---

*Sustituye `RUTA_DEL_LOGCAT` por el path real antes de usar.*
