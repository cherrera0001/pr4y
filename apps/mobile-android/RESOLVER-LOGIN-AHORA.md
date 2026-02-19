# Cómo resolver el login con Google (pasos concretos)

El fallo **ApiException 10 (DEVELOPER_ERROR)** se resuelve **solo en Google Cloud Console**. No hace falta cambiar más código.

---

## Qué hacer (quien tenga acceso a GCP)

1. **Abre** [Google Cloud Console](https://console.cloud.google.com/) → el proyecto donde está el Web Client ID que usa la app (pr4y-backend-server / `583962207001-...apps.googleusercontent.com`).

2. **Ve a** **APIs y servicios** → **Credenciales**.

3. **Cliente Android:**
   - Si ya existe un cliente de tipo **Android** en ese proyecto, ábrelo.
   - Si no existe, **Crea credenciales** → **ID de cliente de OAuth** → tipo **Android**.

4. **Rellena (o corrige):**
   - **Nombre:** el que quieras (ej. "pr4y-android").
   - **Nombre del paquete:** `com.pr4y.app`
   - **Huella digital del certificado SHA-1:**  
     `7B:6D:C7:07:9C:34:73:9C:E8:11:59:71:9F:B5:EB:61:D2:A0:32:25`  
     (Esta huella salió del logcat del APK que tienes instalado; ver `docs/ANALISIS-LOGCAT-20260218-204604.md`.)

5. **Guardar.** Los cambios pueden tardar unos minutos en aplicarse.

6. **Probar:** abre la app en el móvil, pulsa "Continuar con Google" y elige la cuenta. Si todo está bien, no debería volver a salir el mensaje "Google no reconoce esta instalación...".

---

## Si usas otro certificado (otro APK)

Si más adelante firmas con **otro keystore** (por ejemplo release con un keystore distinto), esa build tendrá **otra SHA-1**. Entonces:

- O bien añades esa **nueva** SHA-1 como segunda huella en el mismo cliente Android en GCP (Google permite varias),
- O bien obtienes la SHA-1 de ese keystore con:
  - `.\gradlew :app:showDebugSha1` (desde `apps/mobile-android`) para debug, o
  - `keytool -list -v -keystore <ruta> -alias <alias>` para release
  y la registras en GCP.

---

## Resumen

| Dónde | Qué |
|-------|-----|
| **Google Cloud Console** | Cliente OAuth tipo **Android**, package `com.pr4y.app`, SHA-1 `7B:6D:C7:07:9C:34:73:9C:E8:11:59:71:9F:B5:EB:61:D2:A0:32:25`. |
| **Código** | Ya está: mensaje claro al usuario y log con referencia a la doc cuando ocurre el error 10. |

Cuando esa SHA-1 y el package estén en GCP, el login con Google debería funcionar en el APK que tenías al capturar el logcat.
