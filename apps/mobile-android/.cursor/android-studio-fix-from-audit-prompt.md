# Pedida: Aplicar correcciones a partir del informe de auditoría

## Contexto
Ya existe un **Informe de Auditoría de Logcat** para la app PR4Y v1.2.5. El informe clasificó cada tipo de error en: **corregible desde la app**, **no corregible** o **parcial**. Tu tarea es aplicar **solo** las acciones que el informe recomienda y **no** inventar fixes para lo que el auditor dijo que se ignore.

## Qué SÍ debes hacer

1. **Rendimiento / Main Thread**  
   El informe indica que ya está mitigado con lazy init y ViewModel (v1.2.5) y que el tiempo actual es aceptable.  
   - **Acción:** Revisar que no quede trabajo pesado (Keystore, Retrofit, AuthTokenStore) en el main thread en el arranque o en la primera composición. Si encuentras algo, moverlo a `Dispatchers.IO` o ViewModel. Si ya está así, no cambiar nada.

2. **Documentar qué ignorar**  
   - **Acción:** Añadir un archivo breve (por ejemplo `docs/LOGCAT_DEV.md` o una sección en el README del módulo Android) que diga:
     - En dispositivos Xiaomi/MIUI con Android 15 es normal ver: LB / FrameInsert, avc denied, RTMode has no permission, UserSceneDetector, MiuiBoosterUtils, userfaultfd.
     - No son fallos de la app; se pueden filtrar en Logcat para trabajar más cómodo.
     - Ejemplo de filtro (si aplica): excluir tags `LB`, `om.pr4y.app.dev` para los "fail to open node", o filtrar solo por el paquete `com.pr4y.app.dev` y asumir que el resto es ruido conocido.

3. **Instalación / “invalid package”**  
   El informe dice que es parcial y que se soluciona con desinstalación limpia vía ADB; es comportamiento normal en desarrollo.  
   - **Acción:** No añadir código para “arreglar” el mensaje. Opcional: en la misma doc anterior, mencionar que en desarrollo (instalación por ADB/Android Studio) pueden aparecer mensajes de “invalid package” o “Can't load position” y que es esperable.

## Qué NO debes hacer

- **No** proponer ni añadir código para: silenciar LB, FrameInsert, avc, RTMode, UserSceneDetector, MiuiBoosterUtils o userfaultfd. El informe dejó claro que no son corregibles desde la app y que deben ignorarse.
- **No** añadir permisos, `meta-data` ni hacks en el manifest para “evitar” denegaciones de SELinux o de drivers de Xiaomi, salvo que ya existan en el proyecto y el informe los cite.
- **No** tocar la lógica de negocio, AuthTokenStore, DekManager ni red solo para “reducir logs”; el informe indica que PR4Y_NETWORK y PR4Y_CRYPTO están correctos.

## Criterio de éxito
- El proyecto sigue compilando y la app arranca igual que antes.
- Existe documentación (aunque sea mínima) que explique qué logs ignorar en dev (Xiaomi/MIUI) y, si aplica, un ejemplo de filtro de Logcat.
- No hay cambios de código destinados a “arreglar” LB, FrameInsert, avc, RTMode ni userfaultfd.

## Resumen en una línea
A partir del informe de auditoría: solo aplicar mejoras de rendimiento si queda algo en main thread, documentar los logs a ignorar (y opcionalmente filtro Logcat), y no tocar nada que el informe haya marcado como “no corregible desde la app”.
