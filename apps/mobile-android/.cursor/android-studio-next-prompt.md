# Qué pedir ahora a Android Studio

Copia y pega **una** de estas instrucciones al agente de Android Studio, según lo que quieras hacer.

---

## Opción A – Verificar que todo sigue bien tras la auditoría

```
Contexto: Se aplicaron cambios por el Informe de Auditoría (delay antes de initBunker, cache una vez por versión, meta-data MIUI). El flavor es dev (com.pr4y.app.dev).

Haz esto:
1. Compila e instala la app en el dispositivo/emulador (Run o Apply Changes).
2. Abre la app y deja que llegue a la pantalla de login.
3. Revisa Logcat (filtro por com.pr4y.app.dev o PR4Y_DEBUG): confirma que no hay crashes ni errores nuevos que no estén ya documentados en LOGCAT_DEV.md (LB, FrameInsert, avc, RTMode se ignoran).
4. Si quieres, anota cuántos frames salta Choreographer en el primer arranque (tag Choreographer); no hace falta “arreglar” nada, solo verificar que el proyecto está estable.

No cambies código salvo que encuentres un fallo real (crash o bloqueo). No toques applicationId ni los flavors.
```

---

## Opción B – Siguiente fase: Google Auth

```
Contexto: La app PR4Y (com.pr4y.app.dev) está estable tras la auditoría. El siguiente paso es la fase de pruebas de Google Auth.

Haz esto:
1. Revisa la implementación actual de login con Google (LoginScreen, AuthRepository, credenciales en build.gradle / BuildConfig).
2. Comprueba que el flujo compila, se instala y que en el dispositivo/emulador puedes pulsar “Iniciar con Google” sin crash.
3. Si algo falla (por ejemplo SHA-1, client ID, o dependencias de Play Services), indica los pasos concretos para corregirlo y aplica solo los cambios necesarios.
4. No modifiques la lógica de DekManager, sync ni el resto de la app; solo lo relacionado con Google Sign-In y el envío del token al backend.

Criterio de éxito: la app abre, muestra login, y el botón de Google no crashea y (si el backend está listo) permite completar el acceso.
```

---

## Opción C – Solo “¿está todo bien?”

```
Compila e instala la app PR4Y (flavor dev). Comprueba que arranca hasta la pantalla de login sin crash. Si ves errores en Logcat, descarta los que estén en LOGCAT_DEV.md como ruido de MIUI/Android 15. Responde: OK o lista de 1–3 problemas reales (crash/ANR) si los hay.
```

---

**Recomendación:** Usa **Opción A** para cerrar la verificación post-auditoría y luego **Opción B** cuando quieras avanzar con Google Auth.
