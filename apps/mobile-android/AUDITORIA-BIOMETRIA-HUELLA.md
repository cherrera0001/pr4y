# Auditoría: Mecanismo de huella / biometría en la app PR4Y

## 1. Resumen ejecutivo

La huella (biometría) en la app **no desbloquea datos cifrados a nivel de hardware**: se usa como **atajo de UX** para no tener que escribir la “Clave de Privacidad” (passphrase). La passphrase se guarda en almacenamiento cifrado y, al usar la huella, la app la lee y con ella deriva la KEK y desenvuelve la DEK del servidor. Por tanto, “habilitar la huella” significa **habilitar ese atajo**: que en la pantalla de desbloqueo se ofrezca “Usar Biometría” y que, al autenticarse con huella, se use la passphrase guardada para desbloquear.

En la auditoría se identifican **por qué la huella puede no mostrarse o no estar “habilitada”**, el flujo completo (lectura de huella, desbloqueo, sesión) y las limitaciones del diseño actual.

---

## 2. Condiciones para que la huella esté “habilitada” y visible

La UI muestra el botón “Usar Biometría” y/o lanza el diálogo de huella **solo** cuando se cumplen **todas** estas condiciones.

### 2.1 Estado de la pantalla: `Locked` (no `SetupRequired` ni `Loading`)

- **`UnlockViewModel.checkStatus()`** decide entre:
  - **`Locked`**: hay wrapped DEK en el servidor (`GET /v1/crypto/wrapped-dek` exitoso con body) y no es flujo “empezar de cero”.
  - **`SetupRequired`**: no hay wrapped DEK o el usuario pulsó “¿Olvidaste tu clave?” → `startFresh()`.

Si por **401 / sesión expirada** o por **error de red** no se obtiene el wrapped DEK, se muestra `SetupRequired` o `SessionExpired`/`Error`, y **nunca** se llega a `Locked`. En esos casos la huella no se ofrece aunque el dispositivo y la configuración la soporten.

### 2.2 Dispositivo capaz de biometría fuerte: `canUseBiometrics == true`

En **UnlockScreen**:

```kotlin
val biometricManager = remember { BiometricManager.from(context) }
val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
```

- Se usa **solo `BIOMETRIC_STRONG`** (huella de clase fuerte, no solo “conveniente”).
- Si el dispositivo no tiene sensor, no tiene huellas registradas (`BIOMETRIC_ERROR_NONE_ENROLLED`), o solo ofrece biometría débil, `canAuthenticate` es `false` y la opción de huella no se muestra como disponible.

### 2.3 Usuario ya “activó” el atajo: `biometricEnabled == true`

```kotlin
biometricEnabled = authRepository.isBiometricEnabled()
// AuthTokenStore:
fun isBiometricEnabled(): Boolean = prefs.contains(KEY_PASSPHRASE)
```

- **Solo hay passphrase guardada** (y por tanto “biometría habilitada”) cuando en algún momento se llamó a **`savePassphrase(passphrase)`**.
- Eso ocurre únicamente cuando el usuario desbloquea (o configura el búnker) **y** se pasa `useBiometrics == true` a `unlockWithPassphrase` / `setupNewBunker`.

En la UI actual, **el checkbox “Activar acceso rápido con huella” solo existe en la pantalla de configuración inicial** (`SetupRequired`):

```kotlin
// UnlockScreen.kt - solo en isSetup
if (isSetup && canAuthenticate) {
    Row(...) {
        Checkbox(checked = rememberWithBiometrics, ...)
        Text("Activar acceso rápido con huella", ...)
    }
}
```

En estado **`Locked`** no se muestra ese checkbox. Al pulsar “Desbloquear” se llama:

```kotlin
viewModel.unlockWithPassphrase(passphrase, rememberWithBiometrics, context)
```

Con `Locked`, `rememberWithBiometrics` no se muestra en pantalla y queda en su valor por defecto **`false`**. Por tanto:

- Si el usuario **no marcó** “Activar acceso rápido con huella” en la **primera** vez (SetupRequired), **nunca** se guarda la passphrase.
- Desde la pantalla **Locked** no hay forma de activar la huella después: no hay opción “Activar huella para la próxima vez”.

**Conclusión:** El motivo más probable por el que “no se habilita la huella” es que **en la primera configuración del búnker no se marcó el checkbox** “Activar acceso rápido con huella”, y desde entonces la pantalla Locked no ofrece esa opción.

---

## 3. Flujo completo: lectura de huella → desbloqueo → sesión

### 3.1 Dónde se usa la huella

| Lugar | Uso |
|------|-----|
| **UnlockScreen** | `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` para decidir si mostrar la opción; `BiometricPrompt` para “Desbloquear Búnker”. |
| **UnlockViewModel.unlockWithBiometrics(context)** | Obtiene la passphrase guardada; si existe, llama `unlockWithPassphrase(savedPass, true, context)`; si no, muestra error “Biometría no configurada correctamente”. |

No hay uso de huella en Login (Google), ni en Settings, ni para proteger otros datos fuera del flujo de desbloqueo del búnker.

### 3.2 Secuencia al usar la huella

1. Usuario en **UnlockScreen** con estado **Locked**, `biometricEnabled == true`, `canUseBiometrics == true`.
2. **LaunchedEffect(uiState)** detecta `Locked` con esas condiciones y, tras 300 ms, llama a **`launchBiometrics()`**.
3. Se muestra **BiometricPrompt** (“Desbloquear Búnker”, “Usar clave” como botón negativo).
4. Usuario autentica con huella → **onAuthenticationSucceeded** → **`viewModel.unlockWithBiometrics(context)`**.
5. ViewModel hace **`getPassphrase()`**; si hay passphrase guardada, **`unlockWithPassphrase(savedPass, true, context)`**.
6. Dentro de `unlockWithPassphrase`: **GET wrapped-dek** (con refresh si 401), se deriva KEK con la passphrase y salt del servidor, se desenvuelve la DEK, se llama **`DekManager.setDek(dek)`**, y si procede **`finalizeUnlock(context)`** (sync, estado Unlocked).
7. Navegación: **onUnlocked()** → `isUnlocked = true` → se muestra Home (o Welcome).

La **sesión** (tokens en `AuthTokenStore`) no cambia por usar huella: la huella solo sustituye **introducir la passphrase a mano**; el acceso a la API sigue siendo con el mismo access/refresh token.

### 3.3 Persistencia de la passphrase y del “habilitado”

- **Guardado:** `AuthTokenStore.savePassphrase(passphrase)` (EncryptedSharedPreferences, clave `KEY_PASSPHRASE`).
- **Lectura:** `AuthTokenStore.getPassphrase()` solo para el flujo de `unlockWithBiometrics`.
- **Borrado:**
  - **`clearPassphrase()`**: al pulsar “¿Olvidaste tu clave?” → `startFresh()`.
  - **`tokenStore.clear()`** (logout): borra access, refresh **y** passphrase. Tras cerrar sesión o sesión expirada, la huella queda deshabilitada hasta que el usuario vuelva a configurar el búnker y marque la opción (si se ofreciera de nuevo).

La passphrase **no** está ligada al hardware biométrico (no se usa `setUserAuthenticationRequired` en Keystore para esa clave). Quien tenga acceso al almacenamiento cifrado de la app (p. ej. root/backup) podría en principio intentar acceder a esa preferencia; está protegida por EncryptedSharedPreferences con MasterKey.

---

## 4. Desbloqueo de la app vs. desbloqueo del búnker

- **Pantalla de desbloqueo del búnker (Unlock):** La que pide passphrase o huella. Solo se muestra si `loggedIn && !isUnlocked`.
- **`DekManager.tryRecoverDekSilently()`:** En el arranque (MainActivity), si hay DEK envuelta en almacenamiento local (Keystore), se intenta desenviar **sin** passphrase ni huella. Si tiene éxito, `isUnlocked = true` y se **salta** la pantalla Unlock. Eso es independiente de la huella: la huella no participa en ese “silent unlock”.

Por tanto:
- La huella **solo** se usa en la **pantalla Unlock** cuando el usuario debe introducir (o “revelar”) la passphrase para obtener la DEK desde el servidor.
- No hay “desbloquear la app con huella al abrirla” en el sentido de que la app pida huella en cada apertura; si `tryRecoverDekSilently()` tiene éxito, no se muestra Unlock.

---

## 5. Poder real de la huella en el diseño actual

| Capacidad | ¿Implementado? |
|-----------|----------------|
| Evitar escribir la passphrase en Unlock (atajo UX) | Sí |
| Habilitar/deshabilitar desde Locked (sin “olvidar clave”) | No: solo se puede activar en la primera configuración (SetupRequired). |
| Desbloqueo al abrir la app (p. ej. cada vez que se abre) | No: el “silent unlock” no usa huella. |
| Protección de la passphrase con “user authentication required” en Keystore | No: la passphrase está en EncryptedSharedPreferences, no atada a huella en hardware. |
| Uso de huella en Login (Google) | No. |
| Uso de huella en otras pantallas (Settings, etc.) | No. |

---

## 6. Motivos por los que la huella “no se habilita” o no aparece

Resumidos:

1. **No se marcó “Activar acceso rápido con huella” en la primera configuración** y no hay forma de activarlo después desde Locked.
2. **El dispositivo no reporta BIOMETRIC_STRONG:** sin sensor, sin huellas registradas, o solo biometría débil → `canAuthenticate == false`.
3. **No se llega a estado Locked:** 401/sesión expirada, error de red o “Empezar de cero” → SetupRequired/Error/SessionExpired → no se muestra la opción de huella.
4. **Tras logout o sesión expirada** se borra la passphrase → `biometricEnabled == false` hasta volver a configurar y marcar la opción (en SetupRequired).
5. **Permisos:** En el manifest están `USE_BIOMETRIC` y `USE_FINGERPRINT` (maxSdkVersion 28). En Android 9+ no suele hacer falta solicitar permiso en runtime para usar BiometricPrompt; si en algún dispositivo fuera necesario, podría afectar.

---

## 7. Recomendaciones

1. **Permitir activar la huella desde Locked:** Añadir en la pantalla Locked una opción tipo “Usar huella la próxima vez” al desbloquear con passphrase (guardar passphrase en ese momento si el usuario lo confirma y `canAuthenticate`).
2. **Diagnóstico en pantalla (opcional):** Mostrar brevemente por qué no se ofrece huella (ej. “Activa la huella en la primera configuración”, “Añade una huella en Ajustes del sistema”, “Sesión expirada”).
3. **Fallback a BIOMETRIC_WEAK (opcional):** Si se quiere soportar dispositivos que solo ofrecen biometría débil, usar `Authenticators.BIOMETRIC_WEAK` como fallback cuando `BIOMETRIC_STRONG` no esté disponible.
4. **Reforzar seguridad (opcional):** Valorar almacenar la passphrase (o un secret derivado) en una clave de Keystore con `setUserAuthenticationRequired(true)` y timeout, de modo que solo se pueda leer tras autenticación biométrica reciente.

---

## 8. Referencia de código

| Componente | Archivo | Responsabilidad |
|------------|---------|-----------------|
| Estado “biometría habilitada” | `AuthTokenStore.kt` | `isBiometricEnabled()` = existe KEY_PASSPHRASE; `savePassphrase` / `getPassphrase` / `clearPassphrase`; `clear()` borra passphrase. |
| Lógica de Unlock | `UnlockViewModel.kt` | `checkStatus` → Locked( canUseBiometrics, biometricEnabled ); `unlockWithPassphrase(..., useBiometrics)` guarda passphrase si useBiometrics o ya estaba habilitada; `unlockWithBiometrics` usa passphrase guardada. |
| UI Unlock | `UnlockScreen.kt` | `BiometricManager` + `canAuthenticate(BIOMETRIC_STRONG)`; checkbox “Activar acceso rápido con huella” solo en SetupRequired; LaunchedEffect lanza huella en Locked si biometricEnabled && canUseBiometrics. |
| Sesión / logout | `AuthRepository.kt` | `logout()` → `tokenStore.clear()` (borra passphrase). |

Con esto se cubre el motivo por el que la huella no se habilita, el flujo de lectura de huella, desbloqueo de la app y manejo de sesión, y el alcance real del uso de la huella en la app.
