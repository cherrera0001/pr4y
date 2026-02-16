# Auditoría de flujos PR4Y (backend → API → front)

La base del sistema es **backend, servicios y API**. El front (web y app Android) consume y sirve esos datos.

---

## 1. Flujo de identidad (auth)

| Capa | Responsabilidad |
|------|-----------------|
| **Backend** | `/auth/google`, `/auth/refresh`, `/auth/logout`. Valida idToken de Google, crea/actualiza usuario, emite JWT (access + refresh). |
| **API** | Fastify + JWT. `server.authenticate` extrae `userId` del token. |
| **App** | Login con Credential Manager → envía idToken a `POST /auth/google` → guarda tokens en `AuthTokenStore` (EncryptedSharedPreferences). Refresh en interceptor cuando 401. |

**Sesión:** Los tokens viven en el dispositivo. No se "crea usuario" en cada apertura; el backend ya tiene al usuario tras el primer login con Google.

---

## 2. Flujo DEK (desbloqueo / cifrado)

| Capa | Responsabilidad |
|------|-----------------|
| **Backend** | `GET/PUT /crypto/wrapped-dek`. Almacena por usuario la DEK envuelta (cifrada con KEK derivada de la passphrase del usuario). No ve la passphrase ni la DEK en claro. |
| **API** | Valida schema (kdf, wrappedDekB64, longitud, base64). Rate limit. |
| **App** | Primera vez: genera DEK, deriva KEK(passphrase), envuelve DEK, `PUT` al servidor. Siguientes: `GET` wrapped DEK, deriva KEK con la passphrase (tecleada o liberada por huella desde almacenamiento local), desenvuelve, guarda DEK en memoria y opcionalmente en KeyStore (persistencia local). |

**Posibles fallos observados:**

- **"No recupera la frase"**: La passphrase se guarda en `AuthTokenStore` solo si el usuario marca "Usar seguridad biométrica" y el guardado es síncrono (`commit()`). Si el dispositivo borra EncryptedSharedPreferences (actualización, datos de app, etc.), la frase guardada se pierde y solo queda introducir la frase; si no coincide con la usada al crear la DEK en el servidor, el desenvelope falla (mensaje claro: "La frase no coincide").
- **"Tras un tiempo no pide huella, solo frase"**: Si `tryRecoverDekSilently()` falla (KeyStore no disponible, clave invalidada, etc.) y además no hay passphrase guardada (por el motivo anterior), la pantalla Unlock muestra solo el campo de frase. La huella solo "libera" la passphrase guardada; si esa guardada ya no está, no hay huella posible hasta volver a guardar la frase (o usar "Empezar de cero").

---

## 3. Flujo de sincronización (sync)

| Capa | Responsabilidad |
|------|-----------------|
| **Backend** | `GET /sync/pull`, `POST /sync/push`. Pull: devuelve registros del usuario (cursor, límite). Push: acepta/rechaza registros, last-write-wins, devuelve serverTime y rechazados. |
| **API** | Schema: recordId, type, version, encryptedPayloadB64 (tamaño y patrón base64), clientUpdatedAt, serverUpdatedAt, deleted. No ve contenido en claro (E2EE). |
| **App** | Cifra payload (JSON con title/body o content) con DEK antes de enviar. Descifra al recibir. Outbox local → push; pull → merge en Room. |

**Sanitización:** Los textos (título, cuerpo, diario) se sanitizan en la app antes de montar el JSON y cifrarlo (trim, eliminación de caracteres de control, límite de longitud). El servidor solo valida tamaño y formato del base64.

---

## 4. Recordatorios (alertas)

| Estado actual | Limitación |
|---------------|------------|
| **Backend** | No hay endpoints de recordatorios; es 100 % local en la app. |
| **App** | `ReminderScheduler`: WorkManager periódico 24 h, delay inicial hasta las 9:00. Un solo recordatorio genérico ("Un momento para orar."). |

**Pendiente (observaciones):**

- No es posible hoy **cambiar el horario** (p. ej. elegir 7:00 o 22:00) desde la UI; la hora está fija en código (9:00).
- No hay **personalización por pedido** (1, 2 o todos los pedidos) ni **múltiples recordatorios** (varios horarios o tipos de oración).

Para soportarlo haría falta: (1) persistir preferencias (horario(s), qué recordar) en la app y/o en el backend; (2) WorkManager con ventanas a la hora elegida; (3) UI en Ajustes para configurar hora y opciones.

---

## 5. Sanitización de inputs

| Dónde | Qué se hace |
|-------|-------------|
| **API auth** | Email y contraseña: trim, validación de formato, longitud. |
| **API sync/crypto** | Solo se validan estructura, longitud y patrón del payload cifrado (base64). No se ve texto en claro. |
| **App** | `InputSanitizer`: trim, eliminación de caracteres de control (0x00–0x1F, 0x7F), límite de longitud. Aplicado a título y cuerpo de pedidos y al contenido de entradas de diario antes de cifrar. La passphrase no se sanitiza (cambiaría la clave derivada). |

---

## 6. Resumen de dependencias

- **Login** → backend auth; app guarda tokens.
- **Desbloqueo** → backend devuelve/guarda wrapped DEK; app deriva KEK con passphrase (local o tecleada), desenvuelve DEK, la guarda en memoria/KeyStore.
- **Pedidos y diario** → app cifra con DEK, sync push/pull con backend; backend almacena solo cifrado.
- **Recordatorios** → solo app; sin backend hoy; mejoras requieren persistencia de preferencias y más lógica de scheduling.

Cualquier cambio de comportamiento debe considerar primero el **contrato de la API** y los **servicios del backend**; el front solo refleja y sirve esos datos.
