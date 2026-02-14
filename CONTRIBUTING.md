# Guía de contribución

Gracias por contribuir a PR4Y. Sigue estas pautas para mantener la calidad y, sobre todo, la **seguridad E2EE**.

---

## Cómo manejar el flujo de cifrado (E2EE)

Para que futuros colaboradores no rompan la seguridad de extremo a extremo, es esencial entender y respetar este flujo.

### Resumen del modelo

- **DEK (Data Encryption Key)**: clave simétrica (AES-256) que cifra el contenido de oraciones y notas en el dispositivo. Se genera en el cliente y **nunca** se envía en claro al servidor.
- **KEK (Key Encryption Key)**: derivada de la **passphrase del usuario** (PBKDF2). Se usa solo para envolver la DEK y subirla al servidor como `wrappedDekB64`. El servidor almacena ese blob; no puede derivar la KEK ni la passphrase.
- **Contenido**: antes de guardar en Room (outbox o journal) y antes de cualquier push, el contenido se cifra con la DEK (`LocalCrypto.encrypt`). El servidor solo recibe y almacena `encryptedPayloadB64`.

### Flujo que no debe romperse

1. **Registro / primer uso**: el usuario define una passphrase → se deriva KEK → se genera DEK → la DEK se envuelve con KEK y se sube a la API (`PUT /v1/crypto/dek`). La API guarda solo el blob cifrado.
2. **Desbloqueo**: el usuario introduce passphrase → KEK → se descarga el wrapped DEK → se desenvuelve con KEK → DEK en memoria para cifrar/descifrar.
3. **Crear/editar entrada**: el texto en claro se cifra con la DEK; se persiste en local (y en outbox para sync) **solo** la versión cifrada (`encryptedPayloadB64`).
4. **Sync push**: el cliente envía registros con `encryptedPayloadB64`. La API **nunca** debe recibir ni almacenar texto en claro.
5. **Sync pull**: la API devuelve registros con `encryptedPayloadB64`. El cliente descifra con la DEK en dispositivo para mostrar contenido; en almacenamiento local puede guardar solo el blob cifrado (p. ej. journal E2EE).

### Reglas para no romper E2EE

- **No** enviar al backend ni guardar en tablas de sync ningún campo de “contenido en claro” (título, cuerpo, notas). Solo `encryptedPayloadB64` (o equivalentes cifrados).
- **No** registrar en logs el contenido de oraciones, notas ni passphrase. Tampoco la DEK en claro.
- **No** añadir endpoints que devuelvan o acepten texto en claro de contenido de usuario. Cualquier dato sensible debe ir cifrado por el cliente con la DEK.
- **No** cambiar el contrato de sync para que el servidor “opcionalmente” reciba texto en claro. El servidor debe seguir sin poder descifrar.
- Al añadir nuevos tipos de registro (p. ej. nuevo `type` en sync), el payload debe seguir siendo cifrado en el cliente y enviado como blob; el servidor solo almacena y reparte blobs.

### Dónde está el cifrado en el código

- **Android**: `LocalCrypto` (encrypt/decrypt con DEK), `DekManager` (KEK, wrap/unwrap, persistencia DEK). Outbox y journal usan `encryptedPayloadB64`. Sync: `SyncRepository` solo mueve blobs cifrados.
- **API**: rutas de crypto (wrapped DEK) y sync (pull/push) trabajan solo con identificadores y blobs; sin lógica de descifrado.

Si implementas una nueva pantalla o tipo de dato, sigue el mismo patrón: cifrar con la DEK antes de persistir y antes de cualquier envío a la API.

---

## Estándares de código y PRs

- **TypeScript (API)**: modo estricto, `pnpm run api:lint` y `pnpm run api:typecheck` sin errores.
- **Android**: `./gradlew lint` en `apps/mobile-android` sin fallos.
- **Commits**: mensajes claros; PRs con descripción de qué cambia, por qué y cómo probar.
- **Compatibilidad**: cambios en la API deben mantener compatibilidad hacia atrás o versionado (p. ej. `/v1/`).

---

## Antes de subir código

1. Ejecutar localmente: `pnpm run typecheck`, `pnpm run lint`, y en Android `./gradlew lint`.
2. Opcional pero recomendado: ejecutar el escaneo de secretos (`./scripts/ci/scan-secrets.sh` o `.ps1`) para no introducir credenciales en el repo.

Así se reducen fallos en CI y se aprovechan mejor los minutos gratuitos de GitHub Actions.

---

## Más información

- Reglas para agentes y humanos: [docs/AGENTS.md](docs/AGENTS.md).
- Seguridad y amenazas: [docs/security/](docs/security/).
- API y arquitectura: [docs/README.md](docs/README.md).
