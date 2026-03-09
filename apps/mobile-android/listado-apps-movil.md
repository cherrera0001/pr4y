# Apps instaladas en el móvil (Xiaomi 2312DRA50G)

Generado con `adb shell pm list packages -s` y `pm list packages -3 -i`.

---

## Resumen

| Origen | Cantidad | Descripción |
|--------|----------|-------------|
| **Sistema** | ~310 | Incluidas con Android/Xiaomi/Google (no desinstalables desde Ajustes). |
| **Terceros (Play Store)** | 78 | Instaladas desde Google Play (`installer=com.android.vending`). |
| **Terceros (GetApps / Xiaomi)** | 11 | Instaladas desde la tienda Xiaomi (`com.xiaomi.mipicks`) u otra app del fabricante. |
| **Terceros (Facebook)** | 2 | Instagram/Facebook instalados o actualizados por `com.facebook.system`. |
| **Terceros (manual / sideload)** | 5 | Sin instalador registrado: instalación por ADB, APK directo o build propio. |

---

## Apps de sistema (primeras ~50 de ~310)

Incluyen Android, MIUI, Google (GMS), Qualcomm, etc. Ejemplos:

- `com.android.vending` (Play Store de sistema)
- `com.android.settings`, `com.android.systemui`
- `com.miui.*`, `com.xiaomi.*` (MIUI, Xiaomi)
- `com.google.android.gms`, `com.google.android.gm` (Gmail), `com.google.android.apps.maps`, `com.google.android.youtube`, etc.

*(Lista completa de sistema: cientos de paquetes; se puede regenerar con `adb shell pm list packages -s`.)*

---

## Apps de terceros por origen

### Desde Google Play Store (`com.android.vending`) – 78 apps

- cl.bci.pass, cl.bci.app.personas, cl.bci.app.empresarios, cl.bci.sismo.mach
- cl.santander.smartphone, cl.santander.santanderpasschile
- cl.entel.empresas.app, cl.copec.PagoClick, cl.android
- ch.protonmail.android
- com.adobe.reader, com.amazon.mShop.android.shopping, com.azure.authenticator
- com.authy.authy, com.anthropic.claude, com.openai.chatgpt
- com.alibaba.aliexpresshd, com.canva.editor
- com.discord, com.github.android, com.linkedin.android
- com.spotify.music, com.mercadolibre, com.netflix.mediaclient
- com.tinder, com.ubercab, com.ubercab.driver
- com.whatsapp, com.instagram.android
- com.google.android.calendar, com.google.android.keep, com.google.android.apps.books
- com.google.android.apps.authenticator2, com.google.android.apps.walletnfcrel
- com.google.android.apps.bard, com.google.android.apps.docs.editors.docs/sheets
- com.google.android.apps.chromecast.app, com.google.android.apps.playconsole
- com.xiaomi.wearable, com.xiaomi.smarthome
- org.telegram.messenger, org.coursera.android, org.thoughtcrime.securesms
- org.mozilla.firefox
- net.mullvad.mullvadvpn, net.veritran.becl.prod, net.easyconn.carman.wws
- us.zoom.videomeetings
- com.konylabs.ItauMobileBank, com.principal.dx.cuprumapp
- com.safecard.android, com.global66.cards
- com.activision.callofduty.shooter, com.blizzard.bma
- com.obdautodoctor, com.ovz.carscanner, com.teacapps.barcodescanner
- com.sirma.mobile.bible.android, com.cryart.sabbathschool
- com.starlink.mobile, com.duokan.phone.remotecontroller
- com.fusionmedia.investing, com.musclewiki.macro
- com.deepseek.chat, com.einnovation.temu
- com.zoho.mail, com.zoho.accounts.oneauth
- com.cl.srcei.cedapp, com.cl.srcei.iddigital
- com.badoo.mobile, com.bitchat.droid
- com.google.android.contactkeys, com.google.android.safetycore, com.google.android.ims
- com.google.android.ar.core
- (+ 1 webapk: org.chromium.webapk...)

### Desde tienda Xiaomi / GetApps (`com.xiaomi.mipicks` u otra app)

- com.miui.notes, com.miui.screenrecorder, com.miui.compass
- com.miui.mediaeditor, com.miui.weather2, com.miui.android.fashiongallery
- com.mi.global.shop, com.waze
- com.android.soundrecorder (instalador: mipicks)
- com.xiaomi.scanner (instalador: com.miui.huanji)

### Desde Facebook System (`com.facebook.system`)

- com.facebook.katana (Facebook)
- com.facebook.stella

### Instalación manual / sideload (sin instalador o null)

- **com.pr4y.app** (producción)
- **com.pr4y.app.dev** (desarrollo)
- com.miui.calculator (null)
- cn.wps.xiaomi.abroad.lite (null)
- com.miui.screenrecorder (null en tu listado; a veces aparece mipicks)

---

## Análisis de riesgos y recomendaciones (equipo personal)

*No es asesoramiento de seguridad profesional; es una revisión razonada según buenas prácticas y notoriedad pública de las apps.*

### Prioridad alta – Revisar o reducir

| App / grupo | Riesgo / motivo | Recomendación |
|-------------|------------------|----------------|
| **Facebook + Instagram + Facebook System** | Meta recopila muchos datos; en Xiaomi suele venir **com.facebook.system** de fábrica (actualizador/telemetría aunque no uses las apps). | Si no usas Facebook/Instagram a diario: desinstalar las apps. En Ajustes → Apps, revisar si puedes **desactivar** o restringir permisos a "Facebook App Manager" / "Facebook System". No siempre se puede desinstalar por ser parte del sistema. |
| **Temu** (com.einnovation.temu) | Informes de recopilación agresiva de datos, permisos amplios y dudas sobre cumplimiento de privacidad. | Valorar si la usas; si no, desinstalar. Si la usas, revisar permisos (ubicación, contactos, almacenamiento) y restringir al mínimo. |
| **WPS Office** (cn.wps.xiaomi.abroad.lite) | Instalador **null** (sideload). WPS ha tenido polémicas de privacidad y conexiones a servidores en China. | Si no la necesitas, desinstalar. Si la usas, considerar sustituir por otra (ej. Office oficial, OnlyOffice, o solo Google Docs/Sheets que ya tienes). |
| **Varias apps bancarias** | BCI, Santander, Itaú, Cuprum, Entel, Copec, Global66, Safecard, Veritran, SRCEI… Cada app es un posible vector de ataque y más superficie si el dispositivo se compromete. | Mantener **solo las que uses**. Desinstalar bancos/cuentas que ya no uses. Asegurar que todas vienen de Play Store y están actualizadas. |
| **Duplicados 2FA** | Tienes **Authy**, **Azure Authenticator** y **Google Authenticator**. | Elegir **una** (o dos si necesitas separar personal/trabajo) y desinstalar el resto tras migrar los códigos, para reducir superficie y confusión. |

### Prioridad media – Valorar según uso

| App / grupo | Riesgo / motivo | Recomendación |
|-------------|------------------|----------------|
| **Badoo / Bitchat** | Apps de citas con permisos amplios (ubicación, contactos, etc.). | Si no las usas, desinstalar. Si las usas, revisar permisos en Ajustes. |
| **Starlink** (com.starlink.mobile) | Control del servicio Starlink. | Si no tienes Starlink, desinstalar. Si sí, mantenerla; es oficial. |
| **OBD / escáner coche** (obdautodoctor, ovz.carscanner, carman) | Acceso a datos del vehículo; si son de fuentes poco conocidas, mayor riesgo. | Mantener solo la que uses; asegurarse de que es de desarrollador fiable (Play Store, buena valoración). |
| **Fashion Gallery / Mi Global Shop** (Xiaomi) | Apps de tienda y contenido preinstaladas; suelen incluir anuncios y análisis. | Desinstalar o desactivar si no las usas (GetApps permite desinstalar muchas). |
| **AliExpress** (com.alibaba.aliexpresshd) | Alibaba; permisos y telemetría típicos de e‑commerce. | Revisar permisos; desinstalar si no compras. |
| **Google Wallet / Contact Keys** | Pagos y contactos verificados. Útiles pero sensibles. | Mantener si los usas; asegurar bloqueo de pantalla fuerte y no root. |

### Prioridad baja – Buenas prácticas

| Tema | Recomendación |
|------|----------------|
| **pr4y.app vs pr4y.app.dev** | En el día a día personal, usar solo **producción** (com.pr4y.app). Dejar **com.pr4y.app.dev** para cuando desarrolles/pruebes; no hace falta desinstalarla, pero evita usarla para datos reales. |
| **Juegos** (CoD, Blizzard) | Si no los usas, desinstalar para menos superficie y menos consumo. |
| **Apps Xiaomi de sistema** | En Ajustes → Apps, revisar **com.miui.msa.global** (Miui System Ads) y servicios de “recomendaciones” o “análisis”; desactivar o restringir lo que permita MIUI. |
| **Navegador** | Tienes Chrome y Firefox. Para máxima privacidad en algo sensible, usar Firefox con bloqueo de rastreadores o un perfil separado. |

### Resumen rápido

- **Quitar o restringir:** Facebook/Instagram si no son imprescindibles; Temu si no la usas; WPS si no la necesitas; apps bancarias que ya no uses; una o dos apps de 2FA que sobren.
- **Revisar permisos:** Temu, Badoo, Bitchat, AliExpress, y cualquier app de Xiaomi/GetApps que tenga acceso a ubicación, contactos o almacenamiento.
- **Mantener y actualizar:** Banca que sí uses, Mullvad VPN, Signal (Secure SMS), ProtonMail, Authy o un único Authenticator, pr4y (producción).

---

## Limpieza realizada (Meta, Temu, WPS)

Se ejecutó el script **`limpiar-meta-temu-wps.ps1`**, que:

- **Desinstaló por completo (datos + app):** Facebook, Instagram, Stella, Temu, WPS Office.
- **Borró datos y caché** de los paquetes de sistema Meta (Facebook App Manager, Facebook System, Facebook Services). Esos paquetes siguen en el sistema (Xiaomi no permite deshabilitarlos por ADB), pero ya no tienen datos de tu cuenta.
- **Eliminó carpetas residuales** en `Android/data` y `Android/obb` de todos esos paquetes.

Para intentar **deshabilitar** los componentes Meta de sistema a mano (y que no aparezcan ni se ejecuten):

1. En el móvil: **Ajustes → Aplicaciones → Ver todas** (o “Gestionar aplicaciones”).
2. Busca **Facebook App Manager**, **Facebook System** y **Facebook Services**.
3. Entra en cada una → **Deshabilitar** (si la opción está disponible; en algunos Xiaomi no aparece).

Sin root no se pueden eliminar por completo; con los datos borrados y sin usar las apps, el impacto en privacidad queda muy reducido.

---

## appMall / tienda Xiaomi (GetApps)

- **com.mi.global.shop** (Mi Global Shop): deshabilitada por ADB; ya no aparece en el launcher.
- **com.xiaomi.mipicks** (GetApps / “appMall”): es app de sistema protegida; Xiaomi no permite deshabilitarla por ADB. Se borraron sus datos (`pm clear`). Para no verla: Ajustes → Apps → GetApps → Deshabilitar (si sale la opción), o usar un launcher que permita ocultar apps (Nova, Lawnchair, etc.).

---

## Por qué Xiaomi limita y cómo tener control real ("modo administrador")

En Android **no existe** un "modo administrador" como en Windows que desbloquee todo con un clic. Xiaomi marca ciertas apps como **protegidas**: ni Ajustes ni ADB normal pueden deshabilitarlas.

Las opciones que dan control real son:

### 1. Ocultar la app (sin root, sin desbloquear)

- Instalar un **launcher** que permita ocultar apps del cajón (Nova Launcher, Lawnchair, etc.) y ocultar GetApps / appMall. La app sigue instalada pero no la ves.

### 2. Desbloquear bootloader y root o ROM personalizada

**Importante:** El proceso oficial y las reglas de Xiaomi **cambian** (región, modelo, año). Usa siempre la **página oficial** y la **herramienta oficial** como fuente de verdad:

- **Página oficial (inglés):** [Mi Unlock](https://en.miui.com/unlock/)  
- **Descarga de la herramienta:** [Mi Unlock – Download](https://en.miui.com/unlock/download_en.html) (desde el menú Downloads de la misma web).

**Requisitos típicos (comprueba en la web oficial):**

- Teléfono con MIUI/HyperOS oficial, funcionando bien.
- Cuenta Xiaomi válida; en políticas recientes suelen pedir cuenta con **antigüedad mínima** (ej. 180 días) e historial “limpio”.
- En el móvil: **Opciones de desarrollador** activadas, **OEM unlocking** y **Depuración USB** activados, y **vincular la cuenta** en “Mi Unlock status”.
- Conexión a internet en el móvil (a veces exigen **datos móviles**, no solo Wi‑Fi).
- **Modo Fastboot** (apagado, luego Volumen abajo + encendido), conectado por USB al PC con la herramienta Mi Unlock instalada.

**Restricciones recientes (2024–2025, pueden variar):**

- A menudo **solo un dispositivo por cuenta y por año** puede desbloquearse.
- Cuenta con antigüedad mínima (ej. 180 días); cambios recientes de seguridad (teléfono, email) pueden hacer que rechacen la solicitud.
- Dispositivos **chinos** y **globales** suelen estar separados (no se desbloquea un modelo chino con cuenta global y viceversa).
- Si te aprueban y **no desbloqueas en un plazo** (ej. 14 días), a veces hay que volver a solicitar.
- **Desbloquear borra todos los datos** del dispositivo y puede afectar Find Device, huella/cara y algunas apps (p. ej. banca). Haz copia de seguridad antes.

**Después de desbloquear:**

- **Root (Magisk):** permite deshabilitar o desinstalar apps de sistema (p. ej. GetApps) con herramientas como App Manager (F-Droid) o `pm disable`/`pm uninstall` por ADB con root. Riesgos: garantía, detección por apps de banca, cuidado con actualizaciones OTA.
- **ROM personalizada** (Xiaomi.eu, LineageOS, etc.): instalar una ROM que no traiga GetApps. **Comprueba siempre** si tu **modelo exacto** está soportado por esa ROM; no todos los Xiaomi tienen builds disponibles.

### Resumen

| Objetivo | Sin desbloquear | Con bootloader + root o ROM |
|----------|------------------|-----------------------------|
| No ver GetApps | Launcher + ocultar | Deshabilitar/eliminar o ROM sin GetApps |
| Quitar más bloat Xiaomi | No posible | Sí (root o ROM compatible con tu modelo) |

La única forma de tener control “de administrador” sobre esas apps es **desbloquear el bootloader** siguiendo la **guía y requisitos oficiales** de Xiaomi y luego usar root o una ROM adecuada a tu dispositivo.

---

## Cómo volver a generar las listas

Con el móvil conectado por USB (depuración USB activada):

```bash
# Ruta típica de ADB en Windows (Android Studio)
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

%ADB% shell pm list packages -s          > paquetes-sistema.txt
%ADB% shell pm list packages -3 -i       > paquetes-terceros-con-instalador.txt
```

En PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell "pm list packages -3 -i"
```
