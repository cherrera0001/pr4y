# Revisión exhaustiva: NoCredentialException (H1–H5)

## Análisis del estado actual

- **Síntoma:** `NoCredentialException` y `q0.d: No credentials available` en logcat; flujo intenta sin nonce y con nonce y ambos fallan.
- **App:** `com.pr4y.app` (build prod).
- **Contexto:** El usuario indica que todas las variables de entorno están configuradas (Railway, Vercel, Google Console), pero el error persiste. Eso hace aún más necesario **no asumir** que la configuración es correcta y **exigir evidencia en el log** para cada hipótesis.
- **Instrumentación ya en el código:** `LoginDebug.kt` escribe en tag **PR4Y_DEBUG** líneas con `hypothesisId=H1|H2|H3|H4|H5`, con datos concretos (serverClientId, packageName, signingSha1, googleAccountCount, useNonce). Esa versión de la app es la que debe estar instalada para la captura que vas a revisar.

**Regla para quien revise:** No marques ninguna hipótesis como confirmada o rechazada sin citar al menos una línea exacta del logcat. No des por válido “las variables están bien” sin que el log lo demuestre.

---

## Prompt para el agente (revisión exhaustiva)

Copia desde "INICIO PROMPT" hasta "FIN PROMPT" y pásalo al agente que vaya a revisar el logcat.

---

### INICIO PROMPT

Debes hacer una **revisión exhaustiva** del logcat de login de la app Android **com.pr4y.app** (build prod). El fallo es **NoCredentialException** ("Google no reconoce la app"); el usuario afirma que variables de entorno (Railway, Vercel, Google Console) están configuradas, pero el error persiste. No des por hecho que la configuración es correcta: toda conclusión debe basarse en **evidencia extraída del log**.

**Archivo de logcat a analizar:**  
`apps/mobile-android/logcat-login-YYYYMMDD-HHmmss.txt`  
(Reemplaza por el nombre real del archivo que te pasen, por ejemplo `logcat-login-20260218-170136.txt` o el de la nueva captura con PR4Y_DEBUG.)

**Requisitos obligatorios:**

1. **Cargar el archivo** y filtrar todas las líneas con tag **PR4Y_DEBUG** y **PR4Y_ERROR** (y PR4Y_APP si aportan contexto). Si no hay ninguna línea con `hypothesisId=H1` (o H2, H3, H4, H5), debes decir explícitamente: "No hay logs de hipótesis (PR4Y_DEBUG con H1–H5). Es necesaria una nueva captura con la versión instrumentada de la app."

2. **Evaluar cada hipótesis con evidencia del log.** Para cada una, debes:
   - Indicar **CONFIRMADA**, **RECHAZADA** o **INCONCLUSIVA**.
   - Citar **la línea o líneas exactas del log** que justifican el veredicto (copia el texto relevante).
   - Si falta el dato en el log, es **INCONCLUSIVA** (no asumir).

   **H1 — GOOGLE_WEB_CLIENT_ID vacío o mal configurado en runtime**  
   Busca líneas `hypothesisId=H1` y extrae: `serverClientIdLen`, `serverClientIdEmpty`, `suffixOk`.  
   - CONFIRMADA si `serverClientIdEmpty=true` o `suffixOk=false` o longitud sospechosa (p. ej. 0).  
   - RECHAZADA si `serverClientIdEmpty=false`, `suffixOk=true` y longitud razonable (ej. > 50).  
   - INCONCLUSIVA si no aparece H1 en el log.

   **H2 — Package en GCP no coincide con el de la app**  
   Busca líneas `hypothesisId=H2` y extrae: `packageName`.  
   - Debe ser exactamente `com.pr4y.app` para build prod (o `com.pr4y.app.dev` para dev).  
   - CONFIRMADA si el package del log no coincide con el que está en el cliente Android de Google Cloud.  
   - RECHAZADA si coincide.  
   - INCONCLUSIVA si no hay H2.

   **H3 — La SHA-1 del certificado de firma no está registrada en GCP**  
   Busca líneas `hypothesisId=H3` y extrae: `signingSha1`, `packageName`.  
   - CONFIRMADA si hay un valor `signingSha1` (formato AA:BB:CC:...) y el revisor puede comparar con GCP (o indica: "Registra esta SHA-1 en el cliente Android en GCP").  
   - RECHAZADA solo si se verifica que esa SHA-1 ya está en GCP.  
   - INCONCLUSIVA si `signingSha1=null` o no hay H3.

   **H4 — No hay cuentas de Google en el dispositivo**  
   Busca líneas `hypothesisId=H4` y extrae: `googleAccountCount`.  
   - CONFIRMADA si `googleAccountCount=0` o `-1` (sin cuentas o no se pudo leer).  
   - RECHAZADA si `googleAccountCount>=1`.  
   - INCONCLUSIVA si no hay H4.

   **H5 — Petición a Credential Manager mal construida (nonce, etc.)**  
   Busca líneas `hypothesisId=H5` en entry y antes de getCredential; extrae: `useNonce`.  
   - Debe verse el primer intento (useNonce=false) y el segundo (useNonce=true).  
   - CONFIRMADA si hay incoherencia (p. ej. no se llama getCredential) o datos que indiquen request mal formada.  
   - RECHAZADA si ambos intentos aparecen y la request parece correcta.  
   - INCONCLUSIVA si no hay H5.

3. **Resumen final.** Una tabla o lista con: H1, H2, H3, H4, H5 → veredicto + cita de línea(s).

4. **Acción concreta.** Solo después de la tabla:
   - Si H3 es CONFIRMADA o INCONCLUSIVA pero el log sí muestra un valor `signingSha1`: proponer explícitamente "Añadir en Google Cloud Console → Credenciales → Cliente Android (package correspondiente) la huella SHA-1: [valor del log]."
   - Si H1 es CONFIRMADA: indicar dónde se configura GOOGLE_WEB_CLIENT_ID (env / build) y que debe ser el Web Client ID que termina en `.apps.googleusercontent.com`.
   - Si H2 es CONFIRMADA: indicar que el package en GCP debe coincidir exactamente con el del log.
   - Si H4 es CONFIRMADA: indicar que el usuario debe añadir una cuenta Google en Ajustes del dispositivo.
   - Si todo está RECHAZADO y el fallo persiste: proponer siguiente paso (p. ej. captura con logcat filtrado por proceso de la app o revisión de SecurityException / Google Play Services).

No escribas conclusiones genéricas ("revisa la configuración") sin vincularlas a un veredicto H1–H5 y a una línea del log.

### FIN PROMPT

---

*Documento para exigir revisión exhaustiva del logcat con hipótesis H1–H5. Actualizar el nombre del archivo de logcat en el prompt según la captura usada.*
