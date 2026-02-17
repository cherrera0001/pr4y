# Desplegar PR4Y 1.0.0 en Google Play Store

## 1. Requisitos previos

- **Cuenta de Google Play Console** (pago único ~25 USD): [play.google.com/console](https://play.google.com/console)
- **Keystore de release**: un archivo `.jks` o `.keystore` para firmar la app (no uses el debug). Si no tienes uno, créalo una vez y guárdalo bien; si lo pierdes, no podrás actualizar la app en Play.

---

## 2. Crear el keystore de release (solo la primera vez)

En PowerShell (o terminal), en una carpeta segura (no subas este archivo a Git):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkey -v -keystore pr4y-release.keystore -alias pr4y -keyalg RSA -keysize 2048 -validity 10000
```

Te pedirá contraseña y datos (nombre, organización, etc.). **Guarda la ruta del archivo, el alias y las contraseñas** en un lugar seguro.

---

## 3. Configurar firma en el proyecto

En **`local.properties`** (en este monorepo suele estar en la raíz del repo, p. ej. `pr4y/local.properties`; no subas este archivo a Git), añade o completa:

```properties
# Ya deberías tener:
sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
GOOGLE_WEB_CLIENT_ID=583962207001-....apps.googleusercontent.com

# Firma release (rutas en una sola línea, barras normales o doble barra en Windows):
storeFile=C:/ruta/absoluta/a/pr4y-release.keystore
storePassword=TU_STORE_PASSWORD
keyAlias=pr4y
keyPassword=TU_KEY_PASSWORD
```

El `build.gradle.kts` ya usa `signingConfigs.release` para el build `release`; solo necesitas que esas propiedades estén definidas.

---

## 4. Versión para la tienda

La primera versión está configurada como **1.0.0** (`versionName`) y **versionCode = 1** en `app/build.gradle.kts`. Para futuras publicaciones:

- Sube **versionCode** (1, 2, 3…) en cada subida a Play.
- Cambia **versionName** cuando quieras (p. ej. 1.0.1, 1.1.0).

---

## 5. Generar el Android App Bundle (AAB)

Play Store exige **AAB**, no APK, para nuevas apps. En la raíz del proyecto Android:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd c:\Users\c4all\StudioProjects\pr4y\apps\mobile-android
.\gradlew bundleProdRelease
```

El AAB se genera en:

`app/build/outputs/bundle/prodRelease/app-prod-release.aab`

Ese es el archivo que subirás a Play Console.

---

## 6. En Google Play Console

1. **Crear la app** (si no existe): Panel → “Crear aplicación” → nombre, idioma, tipo (app/juego), etc.
2. **Panel de la app** → **Producción** (o una pista de pruebas) → **Crear nueva versión**.
3. **Subir el AAB**: arrastra o selecciona `app-prod-release.aab`.
4. **Completar obligatorio**:
   - **Ficha de la tienda**: título, descripción corta/larga, icono 512×512, capturas (mínimo por tipo de dispositivo).
   - **Clasificación de contenido**: cuestionario y solicitud de clasificación.
   - **Política de privacidad**: URL pública (obligatorio si recoges datos).
   - **Seguridad de la app**: si usas permisos especiales o datos sensibles, declararlos.
5. **Revisar y enviar**: cuando todo esté en verde, envía la versión a revisión.

La revisión suele tardar desde horas hasta unos días.

---

## 7. Resumen de comandos

| Paso | Comando |
|------|--------|
| Keystore (solo 1ª vez) | `keytool -genkey -v -keystore pr4y-release.keystore -alias pr4y -keyalg RSA -keysize 2048 -validity 10000` |
| Generar AAB 1.0.0 | `.\gradlew bundleProdRelease` |
| Salida | `app/build/outputs/bundle/prodRelease/app-prod-release.aab` |

---

## 8. Notas

- **Flavor**: Para la tienda se usa el flavor **prod** (package `com.pr4y.app`). El flavor **dev** (`com.pr4y.app.dev`) es para desarrollo.
- **SHA-1 de release**: En Google Cloud (Credenciales → cliente Android) añade también el **SHA-1 del keystore de release**, para que “Iniciar sesión con Google” funcione en la app publicada:
  ```powershell
  & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore C:\ruta\a\pr4y-release.keystore -alias pr4y
  ```
- **ProGuard**: El build release tiene `isMinifyEnabled = true`; si algo falla en release, revisa `proguard-rules.pro` y los logs de ofuscación.
