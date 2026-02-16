# PR4Y – Mega prompt (instrucciones y rol)

Documento de referencia para agentes de IA y desarrolladores. **Cada función es un requerimiento.** El **backend es siempre el foco**; el front (web y app Android) solo conecta y consume la API.

---

## Rol

Eres el equipo de desarrollo de **PR4Y** (cuaderno de oración privado, E2EE, offline-first). Tu responsabilidad es:

1. **Diseñar e implementar primero en backend/API** (servicios, contratos, persistencia).
2. **Conectar el front** a esos contratos; el front no define reglas de negocio ni fuentes de verdad.
3. Respetar **una función = un requerimiento** (tareas acotadas, implementables y comprobables).
4. Mantener **privacidad** (E2EE, sin contenido en claro en servidor) y **UX simple**.

---

## Principio rector

- **Backend (apps/api):** Fuente de verdad. Servicios, base de datos, reglas de negocio, endpoints.
- **API (Fastify, /v1):** Contrato estable. Validación de entrada, rate limits, respuestas tipadas.
- **Front (web, mobile-android):** Solo consume la API, muestra datos y envía acciones. No inventa flujos que el backend no soporte.

Antes de añadir comportamiento en la UI, debe existir (o crearse) el soporte en backend/API.

---

## Requerimientos por dominio

### 1. Identidad (auth)

| # | Requerimiento | Backend/API | Front |
|---|----------------|-------------|--------|
| 1.1 | Login con Google | `POST /v1/auth/google` recibe idToken, valida con Google, crea/actualiza usuario, devuelve access + refresh JWT. | Envía idToken; guarda tokens; usa Bearer en requests. |
| 1.2 | Refresco de sesión | `POST /v1/auth/refresh` con refreshToken; devuelve nuevo access (y opcionalmente refresh). | Interceptor: ante 401, refresca y reintenta. |
| 1.3 | Cerrar sesión | `POST /v1/auth/logout` invalida o ignora refresh (según diseño). | Borra tokens locales; opcionalmente llama logout. |

### 2. DEK (cifrado de datos)

| # | Requerimiento | Backend/API | Front |
|---|----------------|-------------|--------|
| 2.1 | Almacenar DEK envuelta | `PUT /v1/crypto/wrapped-dek`: body con kdf + wrappedDekB64; persistir por userId. | Tras derivar KEK(passphrase), envuelve DEK y hace PUT. |
| 2.2 | Recuperar DEK envuelta | `GET /v1/crypto/wrapped-dek`: devuelve kdf + wrappedDekB64 del usuario. | GET, deriva KEK con passphrase (tecleada o liberada por huella), desenvuelve DEK. |
| 2.3 | Sin passphrase en servidor | El servidor nunca recibe ni almacena la passphrase. | Solo envía/recibe wrapped DEK; passphrase solo en cliente (y opcionalmente guardada local para huella). |

### 3. Sincronización (sync)

| # | Requerimiento | Backend/API | Front |
|---|----------------|-------------|--------|
| 3.1 | Pull de registros | `GET /v1/sync/pull`: cursor, limit; devuelve nextCursor + records (encryptedPayloadB64, metadatos). | Pagina con cursor; descifra con DEK; merge en local. |
| 3.2 | Push de registros | `POST /v1/sync/push`: records con version, encryptedPayloadB64, clientUpdatedAt, deleted. Last-write-wins; devuelve accepted, rejected, serverTime. | Envía outbox cifrado; aplica rechazos. |
| 3.3 | Validación de payload | API valida schema (tipo, longitud, patrón base64). No ve contenido en claro. | Sanitiza y cifra antes de enviar (trim, control chars, límites). |

### 4. Recordatorios (alertas) — PENDIENTES

Hoy los recordatorios son **solo locales** en la app (WorkManager, 9:00 fijo, un solo mensaje). Para que sean editables y configurables, debe existir **modelo y API en backend** (o al menos contrato claro) y luego la app consumirlo.

| # | Requerimiento | Backend/API | Front |
|---|----------------|-------------|--------|
| 4.1 | **Horario configurable** | Definir modelo: preferencias de recordatorio por usuario (ej. hora, zona). Endpoint `GET/PUT /v1/reminders/preferences` o equivalente que devuelva/guarde hora (y zona) deseada. | Pantalla en Ajustes: selector de hora (y opcionalmente zona); guarda vía API; WorkManager programa a esa hora. |
| 4.2 | **Editar/activar/desactivar recordatorio** | Misma API de preferencias: activo (boolean), hora. | UI para encender/apagar y cambiar hora; sincroniza con backend. |
| 4.3 | **Personalización por pedidos** | Modelo: recordar por “todos”, “1”, “2” o lista de ids (según diseño). Endpoint que persista esta preferencia por usuario. | Ajustes: elegir “recordar por todos los pedidos”, “solo el primero”, “los dos primeros”, etc.; envía a API; notificación muestra según esa regla. |
| 4.4 | **Múltiples recordatorios** | Modelo: varios “slots” (ej. mañana 7:00, noche 21:00). API que guarde lista de { hora, tipo/label?, activo }. | UI para añadir/editar/borrar varios recordatorios; WorkManager o AlarmManager con varias ventanas; contenido de notificación según tipo. |

Cada uno de 4.1–4.4 es **un requerimiento**. Implementación: primero backend (esquema DB, servicios, rutas), luego app que consuma y programe notificaciones locales.

### 5. Sanitización y validación

| # | Requerimiento | Backend/API | Front |
|---|----------------|-------------|--------|
| 5.1 | Validar entradas de auth | Email y contraseña: trim, formato, longitud. Respuesta 400 con código validation_error. | Envía solo tras validar en cliente (opcional pero recomendable). |
| 5.2 | Validar payloads de sync/crypto | Longitud y patrón de encryptedPayloadB64; no interpretar contenido. | Sanitizar texto antes de cifrar (trim, quitar caracteres de control, límite de longitud). |
| 5.3 | No loguear contenido sensible | Nunca loguear passphrase, DEK ni texto en claro de pedidos/diario. | No enviar nunca passphrase al servidor; logs sin contenido de usuario. |

### 6. Flujos de usuario (resumen)

| # | Requerimiento | Notas |
|---|----------------|--------|
| 6.1 | Login → Unlock (si hace falta) → Home | Sesión = tokens. Desbloqueo = cargar DEK (local o desde API con passphrase/huella). |
| 6.2 | Primera vez: crear frase y opcionalmente guardar para huella | Backend: PUT wrapped DEK. Front: guardar passphrase en almacenamiento seguro solo si usuario elige biometría. |
| 6.3 | “Olvidé mi frase”: empezar de cero | PUT nueva wrapped DEK (sobrescribe); front limpia passphrase local y pide nueva. Datos antiguos no recuperables. |
| 6.4 | Momento de oración (carrusel) | Datos desde sync local; descifrar con DEK igual que en Home; una tarjeta por pedido. |

---

## Checklist antes de implementar una función

1. **¿Existe ya el endpoint o el modelo en backend?** Si no, diseñar e implementar primero (esquema DB, servicio, ruta, validación).
2. **¿El contrato API está documentado?** (tipos, códigos de error, límites).
3. **Front:** solo llamadas HTTP, manejo de errores y estados de carga; sin lógica de negocio duplicada.
4. **Una función = un requerimiento:** si mezclas varias (ej. “recordatorios + horario + múltiples”), partir en tareas (4.1, 4.2, 4.3, 4.4).

---

## Referencias en el repo

- **docs/AUDIT-FLOWS.md** — Auditoría detallada de flujos (auth, DEK, sync, recordatorios, sanitización).
- **docs/AGENTS.md** — Reglas generales (seguridad, convenciones, no-invención).
- **apps/api/** — Backend (Fastify, Prisma, servicios). Foco de cualquier nueva capacidad.
- **apps/mobile-android/** — App Android; consume API y programa notificaciones locales según preferencias obtenidas de la API (cuando existan).

---

## Resumen en una frase

**Backend define qué puede hacer el sistema; la API es el contrato; el front solo conecta con eso y muestra/acciona.** Cada función nueva debe tener su requerimiento claro (1 función = 1 requerimiento) y, si afecta datos o reglas, empezar por backend.
