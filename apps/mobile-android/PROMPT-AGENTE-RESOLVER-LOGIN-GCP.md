# Prompt para un agente: resolver login con Google (ApiException 10) de punta a punta

Copia y pega este bloque como **único prompt** a un agente (humano o IA) que vaya a resolver el problema. No hace falta contexto previo.

---

## Prompt (copiar desde aquí)

**Objetivo:** Que el login con Google en la app Android pr4y deje de fallar con ApiException 10 (DEVELOPER_ERROR). La causa es que la combinación **package + SHA-1** del APK no está registrada en el cliente OAuth de tipo **Android** en Google Cloud. La solución es solo en GCP; no hay que cambiar lógica de login en el código.

**Qué debes hacer (en orden):**

1. **Obtener la SHA-1 del build que falla**
   - En el repo, dentro de `apps/mobile-android/`, ejecuta:  
     `.\gradlew :app:signingReport`  
     (o en Linux/Mac: `./gradlew :app:signingReport`).  
   - De la salida, copia la línea **SHA1:** del variant que corresponde al APK instalado (por ejemplo `prodDebug` o `devDebug` si es desarrollo; `prodRelease` si es el APK que se comparte).  
   - Si el usuario tiene un logcat reciente del error, la app ya puede mostrar la SHA-1 en el mensaje de error en pantalla; si no, usa la del `signingReport`.

2. **Identificar proyecto de Google Cloud**
   - El Web Client ID que usa la app está en `apps/mobile-android/local.properties` como `GOOGLE_WEB_CLIENT_ID=...` o en el fallback del código (ej. `583962207001-...apps.googleusercontent.com`).  
   - El **proyecto** en GCP es el que contiene ese cliente OAuth (a menudo se llama algo como "pr4y" o el nombre del backend). No hace falta el ID exacto: quien tenga acceso a GCP sabe cuál es el proyecto de pr4y.

3. **Producir un handoff ejecutable en &lt; 2 minutos**
   - Genera un texto o documento que quien tenga acceso a Google Cloud Console pueda seguir sin dudas:  
     (a) Abrir **APIs y servicios → Credenciales** en el proyecto correcto.  
     (b) Crear credenciales → ID de cliente de OAuth → tipo **Android** (o editar el existente).  
     (c) **Nombre del paquete:** `com.pr4y.app`  
     (d) **Huella digital del certificado SHA-1:** pegar la SHA-1 del paso 1 (formato `AA:BB:CC:...`).  
     (e) Guardar.  
   - Incluye el **enlace directo** a la página de credenciales:  
     `https://console.cloud.google.com/apis/credentials`  
     (la persona selecciona el proyecto si tiene varios).  
   - Si en el repo existe el script `apps/mobile-android/print-gcp-android-oauth-handoff.ps1`, ejecútalo y entrega también su salida como referencia (ahí sale la SHA-1 por variant y el texto listo para pegar).

4. **Opcional pero recomendable**
   - Si la app aún no muestra la SHA-1 en el mensaje de error cuando falla el login (ApiException 10), propón o implementa que en ese mensaje se incluya la huella SHA-1 obtenida en runtime (por ejemplo con `PackageManager` + firma del APK), para que quien tenga que configurar GCP pueda copiarla desde el propio dispositivo o desde un logcat sin ejecutar `signingReport`.  
   - Deja documentado en el repo (por ejemplo en `RESOLVER-LOGIN-AHORA.md` o `COMO-RESOLVER-LOGIN.md`) que, tras añadir la SHA-1 y el package en el cliente Android de GCP, no hace falta recompilar la app; el mismo APK funcionará cuando la configuración se propague.

**Criterio de éxito:** Que una persona con acceso a Google Cloud Console pueda, en menos de 2 minutos y con un solo copy-paste, añadir o corregir el cliente Android con el package y la SHA-1 correctos, y que tras guardar (y esperar unos minutos de propagación) el login con Google funcione en el APK que antes fallaba.

---

## Fin del prompt
