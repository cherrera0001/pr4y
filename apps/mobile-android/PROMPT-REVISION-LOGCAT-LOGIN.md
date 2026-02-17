# Prompt único: qué revisar y corregir según logcat de login

**Copia esta instrucción y úsala en una sola petición (Cursor, agente o tú mismo):**

---

Según el logcat `logcat-login-20260216-214709.txt`, revisa y corrige lo siguiente en la app Android **com.pr4y.app.dev**:

1. **Login con Google (Credential Manager)**  
   El log muestra: `LoginViewModel: Intento 1 falló, probando fallback...`; se abre `CredentialChooserActivity` y `AssistedSignInActivity` de `com.google.android.gms` y luego se cierran sin éxito, y se vuelve a abrir el chooser (fallback).  
   **Revisar y corregir:**  
   - Que el SHA-1 del certificado con el que se firma la app en **este dispositivo** (el que muestra el diálogo "Fallo de Autenticación") esté registrado en Google Cloud Console en el cliente OAuth de tipo **Android** para el package `com.pr4y.app.dev`.  
   - Que el **Web Client ID** (serverClientId) usado en la app coincida con el del proyecto de Google Cloud donde está ese cliente Android.  
   - Que el flujo de fallback en `LoginViewModel` no oculte el diálogo de error con SHA-1 que ayuda al usuario a registrar el fingerprint correcto; si el primer intento falla por "app no reconocida", no intentar fallback sin mostrar antes ese mensaje.

2. **Red / DNS**  
   El log repite: `Failed to resolve using system DNS resolver, getaddrinfo(): No address associated with hostname` (msys).  
   **Revisar y corregir:**  
   - Comprobar que las llamadas a la API (config, login) manejen bien fallos de red y DNS (timeout, mensaje claro al usuario, reintento o "comprueba tu conexión").  
   - No asumir que el dispositivo tiene conectividad válida; detectar cuando la resolución DNS falla y mostrar un mensaje útil en lugar de un error genérico.

3. **MIUI / dispositivo (solo documentar, no bloqueante)**  
   Aparecen: `RTMode: pkgName: com.pr4y.app.dev has no permission`; `LB: fail to open node`; `FrameInsert open fail: No such file or directory`. Son del sistema/vendor (MIUI), no errores de código de la app.  
   **Revisar:**  
   - Dejar documentado que estos mensajes son esperables en algunos dispositivos MIUI y que las meta-data ya añadidas para MIUI (p. ej. en AndroidManifest) son las recomendadas; no intentar "corregir" código por estos logs a menos que se detecte un fallo funcional concreto.  
   **Estado:** Documentado. Los logs RTMode, LB y FrameInsert son conocidos y no requieren acción en la app; las meta-data MIUI en el manifest son las recomendadas.

4. **Resumen de acciones**  
   - Verificar en Google Cloud: SHA-1 + package `com.pr4y.app.dev` + Web Client ID.  
   - Asegurar que el flujo de login muestre el diálogo con SHA-1 cuando Google no reconozca la app, antes de depender del fallback.  
   - Revisar manejo de errores de red/DNS en las llamadas a la API y mensajes al usuario.  
   - Documentar los logs de MIUI/RTMode/LB/FrameInsert como conocidos y no corregibles desde la app.

---

**Acciones correctivas aplicadas (supervisión crítica):**
- **Login:** `LoginViewModel` ya no ejecuta fallback cuando ocurre `NoCredentialException`; se muestra el diálogo "Fallo de Autenticación" con SHA-1, package y Web Client ID (`LoginUiState.ShowAuthFailureHelp`) y se obtiene el SHA-1 en runtime con `getSha1(context)`.
- **Red/DNS:** `AuthRepository.getPublicConfig()` y `googleLogin()` mapean `UnknownHostException`, `ConnectException` y `SocketTimeoutException` al mensaje "Comprueba tu conexión a internet e inténtalo de nuevo."
- **MIUI:** Documentado como conocido; sin cambios de código.

*Generado a partir de logcat-login-20260216-214709.txt (líneas 1-425).*
