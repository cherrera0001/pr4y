# Análisis de logs: cripto, rendimiento, permisos, UX

Resumen de eventos detectados en logcat y cómo se resuelven o mitigan.

---

## 1. Crítico: InvalidAlgorithmParameterException (Caller-provided IV not permitted)

**Qué significa:** En algunos dispositivos (p. ej. Xiaomi, Android 13+) el Keystore de Android rechaza que la app use o almacene un IV (vector de inicialización) propio al cifrar con una clave que tiene `setRandomizedEncryptionRequired(true)`. El resultado es que la app no podía persistir la DEK.

**Solución aplicada (DekManager.kt):**

- **Primero** se intenta el flujo habitual: cifrar la DEK con la clave del Keystore (sin pasar IV en `init`; el Cipher genera el IV).
- Si se lanza **InvalidAlgorithmParameterException** (o falla el cifrado), se usa un **fallback**: la DEK se guarda como valor dentro de **EncryptedSharedPreferences** (solo cifrado por el MasterKey de Security Crypto, sin usar Cipher + clave Keystore para el wrap). Así se evita por completo el uso de IV con la clave del Keystore en ese camino.
- Se guarda un modo (`dek_storage_mode`: `cipher` o `esp_only`) para saber al recuperar si hay que desenvolver con Cipher o leer el valor ya descifrado por ESP.

**Resultado:** La app puede persistir y recuperar la DEK incluso en dispositivos que rechazan el IV con la clave del Keystore.

---

## 2. Alto: Skipped N frames (trabajo en main thread)

**Qué significa:** El mensaje indica que la app hizo demasiado trabajo en el hilo principal y provocó saltos de frames (lag visible).

**Mitigación actual:**

- **DekManager:** `init`, `setDek`, `tryRecoverDekSilently`, `generateDek`, etc. se ejecutan con `withContext(Dispatchers.Default)` o `Dispatchers.IO` en **MainActivity** (`withContext(Dispatchers.IO)` en `initBunker`). La inicialización del Búnker y la recuperación de la DEK no se hacen en el main thread.
- **MainActivity:** El arranque pesado (DekManager.init, AuthTokenStore, recuperación DEK, creación de API) va dentro de `viewModelScope.launch` + `withContext(Dispatchers.IO)`.

**Recomendación si persiste el lag:** Revisar si el primer frame de Compose (ShimmerLoading → Pr4yNavHost) hace trabajo costoso en la primera composición; considerar diferir más contenido o usar `rememberCoroutineScope` + `launch(Dispatchers.Main.immediate)` solo para actualizar estado, manteniendo el trabajo pesado en IO/Default.

---

## 3. Medio: RTMode – pkgName: com.pr4y.app.dev has no permission

**Qué significa:** MIUI (Xiaomi) intenta aplicar “modo tiempo real” (RTMode) a la app y no tiene permiso. Es un mensaje del sistema, no un fallo de la app.

**Acción:** No requiere cambio en el código. Es esperable en dispositivos Xiaomi y no afecta al login ni al cifrado. Se puede ignorar en el análisis de errores propios de la app.

---

## 4. Bajo: avc: denied { read } (SELinux – vendor_display_prop)

**Qué significa:** SELinux deniega lectura a propiedades del vendor (p. ej. display). Suele venir de capas del sistema (gráficos, OEM), no del código de la app.

**Acción:** No es corregible desde la app; es una política del dispositivo. No afecta a la lógica de pr4y (login, DEK, sync). Se puede ignorar en el análisis funcional.

---

## 5. Bajo: OnBackInvokedCallback is not enabled

**Qué significa:** En Android 13+ el gesto de “atrás” predictivo (predictive back) puede usar `OnBackInvokedCallback`. Si el sistema indica que no está habilitado, el botón/gesto atrás no usa la API nueva.

**Estado en el proyecto:** En `AndroidManifest.xml` la aplicación tiene `android:enableOnBackInvokedCallback="true"`. Con eso la app declara soporte; la animación predictiva depende del dispositivo y de la versión de Android.

**Opcional:** Para una UX más afinada en 13+, se puede registrar `OnBackInvokedCallback` en las pantallas que lo necesiten (p. ej. en actividades o composables con `BackHandler`). No es obligatorio para corregir el mensaje del sistema.

---

## Resumen de prioridades

| Prioridad | Tema                         | Estado |
|----------|------------------------------|--------|
| Crítico  | IV no permitido (DEK)        | Resuelto con fallback ESP en DekManager |
| Alto     | Frames saltados (main thread) | Mitigado (init/crypto en IO/Default) |
| Medio    | RTMode (MIUI)                | Ignorar; mensaje del sistema |
| Bajo     | SELinux vendor_display_prop  | Ignorar; política del dispositivo |
| Bajo     | OnBackInvokedCallback        | Manifest ya habilitado; callback opcional |
