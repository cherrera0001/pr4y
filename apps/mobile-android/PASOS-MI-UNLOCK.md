# Pasos para desbloquear el bootloader (Mi Unlock)

Carpeta del programa: `C:\Users\herre\Downloads\miflash_unlock-en-6.5.224.28`

---

## Orden de ejecución en el PC

### Paso 1: Instalar los controladores USB (solo la primera vez)

- **Ejecutar:** `driver_install_64.exe` (en Windows 64 bits).
- Ubicación: `C:\Users\herre\Downloads\miflash_unlock-en-6.5.224.28\driver_install_64.exe`
- Si Windows pide permisos de administrador, acepta.
- Sigue el asistente hasta terminar la instalación de los drivers. Así el PC reconocerá el móvil en modo Fastboot.

### Paso 2: Preparar el móvil (antes de conectar para desbloquear)

1. **Opciones de desarrollador**
   - Ajustes → Acerca del teléfono → toca 7 veces en "Versión de MIUI" (o "Versión del kernel").
   - Vuelve atrás → Ajustes → Configuración adicional → Opciones de desarrollador.

2. **Activar**
   - **Desbloqueo OEM** (OEM unlocking): activado.
   - **Depuración USB**: activada.

3. **Vincular cuenta Xiaomi al teléfono**
   - En Opciones de desarrollador, entra en **"Estado de Mi Unlock"** / **"Mi Unlock status"**.
   - Inicia sesión con tu cuenta Xiaomi si no lo has hecho.
   - Debe aparecer algo como "Cuenta vinculada a este dispositivo" / "Account linked to this device".

4. **Conexión a internet en el móvil**
   - Asegúrate de tener datos móviles o Wi‑Fi; a veces Xiaomi exige que el dispositivo tenga red al desbloquear.

### Paso 3: Poner el móvil en modo Fastboot

1. **Apaga** el teléfono por completo.
2. Mantén pulsado **Volumen abajo** y, sin soltar, pulsa **Encendido**.
3. Suelta cuando veas la pantalla de **Fastboot** (logo de Xiaomi o texto "Fastboot").
4. Conecta el móvil al PC con el **cable USB** (preferiblemente el original o uno que transmita datos).

### Paso 4: Ejecutar la herramienta de desbloqueo

- **Ejecutar:** `miflash_unlock.exe`
- Ubicación: `C:\Users\herre\Downloads\miflash_unlock-en-6.5.224.28\miflash_unlock.exe`

En la herramienta:

1. **Inicia sesión** con la misma cuenta Xiaomi que vinculaste en el móvil.
2. Si el PC reconoce el dispositivo, debería aparecer el **modelo** del teléfono y un botón **"Unlock"** / **"Desbloquear"**.
3. Lee la advertencia (borrado de datos, garantía, etc.).
4. Pulsa **Unlock** y espera. El móvil se reiniciará y **se borrarán todos los datos**.
5. Al terminar, el bootloader quedará desbloqueado.

---

## Resumen rápido

| Orden | Qué ejecutar | Dónde |
|-------|----------------|-------|
| 1 | `driver_install_64.exe` | En la carpeta Mi Unlock (solo primera vez) |
| 2 | — | Preparar móvil: OEM unlock, depuración USB, vincular cuenta |
| 3 | — | Apagar → Volumen abajo + Encendido → Fastboot → Conectar USB |
| 4 | `miflash_unlock.exe` | En la carpeta Mi Unlock → Iniciar sesión → Unlock |

**No ejecutes** `fastboot.exe` a mano para el desbloqueo; lo usa internamente la herramienta. Solo necesitas `driver_install_64.exe` una vez y luego `miflash_unlock.exe` cada vez que vayas a desbloquear.
