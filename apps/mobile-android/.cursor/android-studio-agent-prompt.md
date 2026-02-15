# Instrucción para agente Android Studio – App PR4Y que “muere” al volver de segundo plano

## Contexto
En Logcat aparece que `com.pr4y.app.dev` es **Force stop** y luego **reinstalada** (`installPackageLI`). El usuario ve la app cerrarse al volver a ella desde segundo plano.

## Diagnóstico (no es crash de la app)
- **Causa:** El cierre lo provoca el **sistema**, no un crash en código. Secuencia típica:
  1. `Force stopping com.pr4y.app.dev ... from process:XXXX` (XXXX = shell/instalador).
  2. `Killing <pid>:com.pr4y.app.dev ... stop com.pr4y.app.dev due to from process:XXXX`.
  3. Poco después: `Force stopping com.pr4y.app.dev ... installPackageLI` y cambio de code path del paquete.
- **Conclusión:** Un **despliegue desde Android Studio** (Run o Apply Changes) mientras la app estaba en segundo plano provoca que el sistema mate el proceso anterior antes de instalar la nueva versión. Es **comportamiento esperado** del sistema, no un bug de la app.

## Qué debe hacer el agente
1. **No intentar “arreglar” el Force stop con código** (no es un bug de la app).
2. **Explicar al usuario** que si hace Run/Apply Changes con la app en segundo plano y luego vuelve a la app, es normal que se cierre y se reinicie con la nueva instalación.
3. **Recomendaciones para el usuario:**
   - Para probar “volver de segundo plano” sin que la mate el instalador: no lanzar Run/Apply Changes mientras la app está en segundo plano; o poner la app en primer plano antes de ejecutar Run/Apply Changes.
   - Para reducir ruido en Logcat en dispositivos Xiaomi/MIUI: filtrar por tag/paquete `com.pr4y.app.dev` y opcionalmente excluir tags como `RTMode`, `LB`, `FrameInsert`, `FinalizerDaemon` (avc), `userfaultfd`; los mensajes `has no permission`, `fail to open node`, `FrameInsert open fail` y `avc: denied` en este contexto son esperables y no indican fallo de la app.
4. **No proponer** cambios de código para “evitar” el Force stop provocado por instalación; no es evitable desde la app y no debe considerarse un defecto a corregir en el proyecto.

## Resumen en una línea
El cierre se debe a Force stop + reinstalación por despliegue (Run/Apply Changes); es comportamiento normal del sistema — no corregir con código, sino explicar al usuario y ajustar el flujo de pruebas (no desplegar con la app en segundo plano si se quiere probar resume sin reinicio).
