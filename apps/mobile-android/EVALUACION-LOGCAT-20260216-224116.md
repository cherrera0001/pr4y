# Evaluación del logcat: logcat-login-20260216-224116.txt

**Archivo:** `logcat-login-20260216-224116.txt` (1645 líneas)  
**Dispositivo:** MIUI / HyperOS (LB, FrameInsert, RTMode, vendor.qti, etc.)  
**App:** `com.pr4y.app.dev` (pid 17921)

---

## 1. Arranque de la app

| Timestamp   | Evento |
|------------|--------|
| 22:41:18.31 | `com.pr4y.app.dev` preLaunchProcess success |
| 22:41:18.32 | Zygote: proceso `com.pr4y.app.dev` (17921) |
| 22:41:18.36 | MainActivity visible (task 30) |
| 22:41:19.40 | **PR4Y_DEBUG:** `Pr4yApp.onCreate\|pkg=com.pr4y.app.dev\|pid=17921\|uid=10099\|userId=0\|hypothesisId=H1,H5` |
| 22:41:20.22 | **PR4Y_APP:** "--- Iniciando Búnker PR4Y (Async) ---" |
| 22:41:20.25 | **PR4Y_CRYPTO:** "Inicializando almacén de hardware (Intento 1)..." |
| 22:41:20.59 | **PR4Y_CRYPTO:** "Almacén cifrado listo." |

**Conclusión:** La app arranca bien: Application onCreate, inicio asíncrono del búnker y almacén cifrado OK. No hay errores de PR4Y en el arranque.

---

## 2. Flujo de login (Credential Manager / Google)

Secuencia de actividades de Google (WindowManager `setClientVisible`):

| Timestamp   | Actividad | Visible |
|------------|------------|--------|
| 22:41:23.91 | `com.google.android.gms/.identitycredentials.ui.CredentialChooserActivity` | **true** |
| 22:41:25.27 | `com.google.android.gms/.auth.api.credentials.assistedsignin.ui.AssistedSignInActivity` | **true** |
| 22:41:27.08 | AssistedSignInActivity | **false** (cierre) |
| 22:41:27.17 | CredentialChooserActivity | **false** (cierre) |
| 22:41:28.71 | CredentialChooserActivity | **true** (segunda vez) |
| 22:41:32.78 | AssistedSignInActivity | **true** (segunda vez) |
| 22:41:34.37 | AssistedSignInActivity | **false** (cierre) |
| 22:41:34.43 | CredentialChooserActivity | **false** (cierre) |

**Interpretación:**  
- Primera ronda: se abre el selector de credenciales (CredentialChooser), luego la pantalla de inicio asistido (AssistedSignIn); ambas se cierran.  
- Segunda ronda: se vuelve a abrir CredentialChooser y AssistedSignIn, y vuelven a cerrarse.  

Eso es coherente con: **primer intento (con nonce) → usuario cierra o no elige cuenta → fallback (sin nonce) → mismo resultado (cierre sin credencial)**.  
En este log **no aparece** el mensaje `LoginViewModel: Intento 1 falló, probando fallback...`, así que o bien este build no lo emite, o el flujo no pasó por ese camino (p. ej. cancelación que no lanza la misma excepción).

**Conclusión:** El flujo de Credential Manager se ejecuta (chooser + assisted sign-in se muestran dos veces). El resultado es que las pantallas se cierran sin que quede registrado en log un éxito ni un error concreto de PR4Y (no hay línea PR4Y con NoCredentialException ni con "Intento 1 falló").

---

## 3. Logs de la app PR4Y en el flujo de login

- **Sí hay:** PR4Y_DEBUG (onCreate), PR4Y_APP (inicio búnker), PR4Y_CRYPTO (almacén).  
- **No hay en este log:**  
  - Ningún log de `LoginViewModel` (p. ej. "Intento 1 falló", "Error en flujo auth").  
  - Ningún log de `NoCredentialException` o diálogo SHA-1.  
  - Ningún log de éxito de login (token, navegación).

Por tanto, en este logcat **no se puede ver** si hubo NoCredentialException, cancelación de usuario o otro fallo; solo que las actividades de Google se abren y cierran dos veces.

---

## 4. Ruido de dispositivo / vendor (no atribuible a la app)

| Tag / Origen | Mensaje típico | Valoración |
|--------------|----------------|------------|
| **RTMode** | `pkgName: com.pr4y.app.dev has no permission` (línea 681) | Conocido en MIUI; no es error de la app. |
| **LB** | `fail to open node: No such file or directory` (muchas líneas, pid 17921) | MIUI; documentado en PROMPT-REVISION-LOGCAT-LOGIN. |
| **om.pr4y.app.dev** | `FrameInsert open fail: No such file or directory` (772) | MIUI FrameInsert; no corregible desde la app. |
| **KeymasterUtils / KeyMasterHalDevice** | `rsp_header->status: -28`, `abort_operation`, `ret: -28` | HAL del dispositivo; no es bug de PR4Y. |
| **RTMode** | `pkgName: com.google.android.gms has no permission` | Sistema/vendor; no es PR4Y. |

Estos mensajes **no indican** un fallo de la app; son propios del fabricante/sistema.

---

## 5. Resumen y recomendaciones

- **Arranque:** Correcto; almacén cifrado se inicializa bien.  
- **Login:** Credential Chooser y Assisted Sign-In se muestran y cierran dos veces; el log **no muestra** la causa (cancelación, NoCredentialException, o éxito).  
- **Falta de evidencia en log:** No hay líneas de `LoginViewModel` ni de excepción de credenciales, por lo que no se puede afirmar si el problema fue SHA-1, usuario cancelando, o red/backend.

**Recomendaciones:**

1. **Confirmar que el build incluye los logs de LoginViewModel** ("Intento 1 falló", "Error en flujo auth", etc.) y, si aplica, el manejo que muestra el diálogo con SHA-1 en NoCredentialException.  
2. **Repetir la prueba** dejando que la pantalla de Google termine (elegir cuenta o esperar el error) y no cerrar manualmente; así debería aparecer o bien éxito o bien NoCredentialException (y el diálogo con SHA-1 si está implementado).  
3. **Filtrar logcat por PR4Y** en la siguiente captura:  
   `adb logcat -s PR4Y_APP:V PR4Y_DEBUG:V PR4Y_CRYPTO:V PR4Y_NETWORK:V` (y el tag que use LoginViewModel, si es distinto) para ver solo líneas relevantes de la app.

---

*Evaluación basada en logcat-login-20260216-224116.txt (líneas 1-1645).*
