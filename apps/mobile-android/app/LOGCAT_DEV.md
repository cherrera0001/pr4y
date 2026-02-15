# Guía de Depuración Logcat - PR4Y (Entorno Xiaomi/MIUI)

Este documento detalla los eventos y errores comunes que aparecen en el Logcat al depurar la aplicación en dispositivos **Xiaomi/MIUI con Android 15**, y cuáles deben ser ignorados por ser comportamiento propio del sistema operativo o del fabricante (OEM).

## 🛑 Logs que DEBEN IGNORARSE (Ruido del Sistema)

Al depurar en un Xiaomi con Android 15, verás una inundación de mensajes que no son fallos de la aplicación PR4Y. Se recomienda filtrarlos para trabajar con mayor comodidad.

| Tag / Mensaje | Causa Raíz | Impacto |
|---------------|------------|---------|
| `LB: fail to open node` | Driver de "Low Battery" de MIUI intentando optimizar la app. | Ninguno. El sandbox de Android 15 bloquea el acceso por seguridad. |
| `FrameInsert open fail` | Motor MEMC de Xiaomi intentando inyectar frames para fluidez visual. | Ninguno. Bloqueado por SELinux (avc denied). |
| `avc: denied` | Denegaciones de SELinux al intentar leer propiedades de hardware. | Ninguno. Comportamiento normal del sandbox de seguridad de Android 15. |
| `RTMode: pkgName: ... has no permission` | MIUI denegando el "Modo Tiempo Real" a una app instalada vía ADB. | Ninguno. Solo afecta a perfiles de rendimiento de juegos. |
| `UserSceneDetector / MiuiBoosterUtils` | Librerías de Xiaomi intentando detectar el contexto de uso de la app. | Ruido visual. |
| `userfaultfd: MOVE ioctl seems unsupported` | Advertencia del recolector de basura (GC) de Android sobre la CPU. | Ninguno. El sistema usa un método de respaldo automáticamente. |

## 🛠️ Instalación y Paquetes

Es normal ver los siguientes logs durante el despliegue desde Android Studio:
- `AppScanObserverService: Try to add a invalid package`: MIUI marca las instalaciones vía USB/ADB como "inválidas" inicialmente por no venir de una tienda oficial. Se soluciona con una desinstalación limpia.
- `Launcher: Can't load position`: El lanzador de MIUI se confunde al re-instalar paquetes con el mismo ID pero distinta firma.

## 🔍 Filtro Recomendado en Android Studio

Para ver solo lo importante (Red, Criptografía y lógica de la App), copia y pega este filtro en la barra de búsqueda del Logcat:

```text
package:mine (tag:PR4Y_APP | tag:PR4Y_NETWORK | tag:PR4Y_CRYPTO | tag:PR4Y_ERROR)
```

O para excluir activamente el ruido de Xiaomi:

```text
-tag:LB -tag:om.pr4y.app.dev -tag:sensors-hal -tag:PowerKeeper
```
