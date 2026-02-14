# PR4Y – Android (Kotlin + Jetpack Compose)

App móvil offline-first para el cuaderno de oración PR4Y.

## Requisitos

- Android Studio Ladybug (2024.2.1) o superior
- JDK 17
- Android SDK 35

## Build

Genera el wrapper de Gradle si no existe (en Android Studio: File > Sync Project, o desde terminal con Gradle instalado):

```bash
gradle wrapper
```

Luego:

```bash
cd apps/mobile-android
./gradlew assembleDebug   # Unix/Mac
# o
.\gradlew.bat assembleDebug   # Windows
```

## Ejecutar en emulador

1. Crea un AVD (Android Virtual Device) con API 26+.
2. Inicia el emulador.
3. Run > Run 'app' en Android Studio, o: `./gradlew installDebug`.

La app usa por defecto `http://10.0.2.2:4000/` como API (emulador → localhost).

## Flujo de la app

1. **Login/Registro**: email + contraseña (mín. 8 caracteres). Tras éxito → pantalla de passphrase.
2. **Passphrase**: primera vez se crea passphrase y se sube la DEK envuelta al servidor; siguientes veces se introduce passphrase para desbloquear.
3. **Home**: lista de pedidos de oración (Room). Botones Diario / Buscar. FAB para nuevo pedido.
4. **Nuevo/Editar pedido**: título + cuerpo. Al guardar se persiste en Room y se añade al outbox cifrado (AES-GCM con DEK) para sync.
5. **Detalle**: ver pedido y botón Editar.
6. **Ajustes**: Sincronizar ahora (push outbox → pull → merge), recordatorio diario (WorkManager), Cerrar sesión.

## Implementado

- Auth: register, login, refresh, logout; tokens en EncryptedSharedPreferences.
- DEK/KEK: PBKDF2 con passphrase, wrap/unwrap DEK, GET/PUT wrapped DEK en API.
- Room: requests, journal, outbox, sync_state.
- Sync: push outbox, pull con cursor, merge en local (last-write-wins); cifrado con DEK.
- Recordatorios: WorkManager periódico (aprox. cada 24 h con delay inicial hasta las 9:00).
- Pantallas: Home, NewEdit, Detail, Journal, Search, Settings, Login, Unlock.

## Estructura

- `data/auth`: AuthTokenStore, AuthRepository
- `data/local`: Room entities y DAOs, AppDatabase
- `data/remote`: ApiService, RetrofitClient
- `data/sync`: SyncRepository
- `crypto`: LocalCrypto (AES-GCM), DekManager (PBKDF2, wrap/unwrap)
- `work`: ReminderWorker, ReminderScheduler
- `ui/screens`: pantallas Compose
- `di`: AppContainer (DB singleton)
