# Generar APK para compartir (testers / familia)

Guía para compilar un APK de release y compartirlo de forma que "Continuar con Google" funcione en los dispositivos de quienes lo instalan.

---

## Requisitos

- **`local.properties`** en `apps/mobile-android/` con:
  - `storeFile`, `storePassword`, `keyAlias`, `keyPassword` (keystore de release)
  - `GOOGLE_WEB_CLIENT_ID` (y opcionalmente `GOOGLE_ANDROID_CLIENT_ID`)
- Keystore de release (p. ej. `keystore-pr4y.jks`) en la ruta indicada por `storeFile` (suele estar en `app/`).

---

## 1. Generar el APK release

Desde la raíz del proyecto Android:

```powershell
cd c:\Users\TU_USUARIO\StudioProjects\pr4y\apps\mobile-android
.\gradlew assembleProdRelease
```

El APK se genera en:

`app/build/outputs/apk/prod/release/app-prod-release.apk`

(Opcional) Si existe la tarea para copiar con nombre versionado:

```powershell
.\gradlew copyPr4yApk
```

El archivo quedará en `dist/pr4y-<version>.apk` (según `versionName` en `app/build.gradle.kts`).

---

## 2. Antes de compartir: SHA-1 en Google Cloud

Para que "Continuar con Google" funcione en los dispositivos de quienes instalen el APK, la **SHA-1 del keystore de release** debe estar registrada en Google Cloud Console.

1. **Obtener la SHA-1** (usa la misma ruta y alias que en `local.properties`):

   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "RUTA_AL_KEYSTORE" -alias TU_ALIAS
   ```

   Ejemplo si el keystore está en `app/keystore-pr4y.jks`:

   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "C:\Users\TU_USUARIO\StudioProjects\pr4y\apps\mobile-android\app\keystore-pr4y.jks" -alias TU_ALIAS_DE_LOCAL_PROPERTIES
   ```

   Te pedirá la contraseña del keystore. Copia la línea **SHA1:** (formato `AA:BB:CC:...`).

2. **En Google Cloud Console**: APIs y servicios → Credenciales → Cliente OAuth 2.0 de tipo **Android** (o créalo).
   - **Nombre del paquete:** `com.pr4y.app` (build prod).
   - **Huella digital del certificado SHA-1:** pega la SHA-1 copiada.
   - Puedes tener varias huellas en el mismo cliente (p. ej. debug + release).
3. Guarda. Los cambios pueden tardar unos minutos en aplicarse.

Si no haces este paso, quienes instalen el APK verán un error al pulsar "Continuar con Google" (p. ej. "No se pudo iniciar sesión con Google...").

---

## 3. Compartir el APK

Envía el archivo `app-prod-release.apk` (o el de `dist/`) por correo, Drive, etc. No hace falta compilar de nuevo cada vez que añadas la SHA-1: **el mismo APK** que ya repartiste funcionará una vez registrada la SHA-1 en GCP.

---

## 4. Si ya repartiste el APK y falla Google

1. Añade la **SHA-1 de release** en Google Cloud (paso 2).
2. **No hace falta** que los usuarios instalen otra versión: que vuelvan a abrir la app y pulsen de nuevo "Continuar con Google".

---

## Referencias

- **`COMO-RESOLVER-LOGIN.md`** — Causas del error de login con Google y resolución paso a paso (sección 0: APK compartido).
- **`DEPLOY-PLAY-STORE.md`** — Publicación en Play Store (AAB, SHA-1, checklist).
- **`README.md`** — Configuración general, Google Sign-In, APK para compartir.
