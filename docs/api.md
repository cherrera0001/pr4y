# PR4Y – API Docs (Contrato v1)

Base URL: `https://your-api.railway.app` (o local `http://localhost:4000`)  
Prefijo rutas: `/v1`  
Formato: JSON  
Auth: Bearer JWT (access token) en cabecera `Authorization: Bearer <accessToken>` para rutas protegidas.

## Errores estándar

```json
{ "error": { "code": "string", "message": "string", "details": {} } }
```

Códigos: `bad_request`, `unauthorized`, `forbidden`, `not_found`, `conflict`, `validation_error`, `internal_error`.

---

## Endpoints

### GET /v1/health

Sin auth. Respuesta: `{ "status": "ok", "version": "1.0.0" }`.

---

### POST /v1/auth/register

Cuerpo: `{ "email": "string", "password": "string" }` (password 8–256 caracteres).  
Respuesta 200: `{ "accessToken", "refreshToken", "expiresIn", "user": { "id", "email", "createdAt" } }`.  
409: email ya registrado.

### POST /v1/auth/login

Cuerpo: `{ "email", "password" }`.  
Respuesta 200: igual que register.  
401: credenciales inválidas.

### POST /v1/auth/refresh

Cuerpo: `{ "refreshToken": "string" }`.  
Respuesta 200: nuevo par accessToken + refreshToken + user.  
401: token inválido o expirado.

### POST /v1/auth/logout

Cuerpo: `{ "refreshToken": "string" }`.  
Respuesta 200: `{ "ok": true }`. Invalida el refresh token.

---

### GET /v1/crypto/wrapped-dek

Auth: Bearer. Devuelve `{ "kdf": { "name", "params", "saltB64" }, "wrappedDekB64" }`.  
404 si el usuario no tiene wrapped DEK.

### PUT /v1/crypto/wrapped-dek

Auth: Bearer. Cuerpo: `{ "kdf": { "name", "params", "saltB64" }, "wrappedDekB64" }`.  
Respuesta 200: `{ "ok": true }`.

---

### GET /v1/sync/pull

Auth: Bearer. Query: `cursor` (opcional), `limit` (opcional, default 100, max 500).  
Respuesta 200: `{ "nextCursor": "string", "records": [ { "recordId", "type", "version", "encryptedPayloadB64", "clientUpdatedAt", "serverUpdatedAt", "deleted" } ] }`.

### POST /v1/sync/push

Auth: Bearer. Cuerpo: `{ "records": [ { "recordId", "type", "version", "encryptedPayloadB64", "clientUpdatedAt", "deleted" } ] }` (máx 100 por request).  
Respuesta 200: `{ "accepted": ["recordId"], "rejected": [ { "recordId", "reason" } ], "serverTime": "ISO8601" }`.  
Conflictos de versión o registros de otro usuario se devuelven en `rejected`.

---

## OpenAPI

Especificación completa: [api-openapi.yaml](api-openapi.yaml).
