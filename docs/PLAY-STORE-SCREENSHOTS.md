# Capturas de pantalla para Google Play – PR4Y

## Qué “no quedó bien” según las directrices

Google exige y **recomienda**:

1. **Barra de estado (muy recomendable):**  
   Editar la barra de notificaciones: quitar elementos que sobren, no mostrar operador ni notificaciones. **Batería, Wi‑Fi y señal deben mostrar niveles máximos** (p. ej. 100 % y señal llena).

2. **Sin marcos de dispositivo** ni texto/imágenes ajenos a la app.

3. **Al menos 4 capturas** con resolución mínima **1080 px** en el lado más corto y relación **9:16** (vertical) o **16:9** (horizontal). Ej.: **1080×1920** (vertical).

4. **Experiencia real** de la app, sin eslóganes que ocupen más del 20 %, sin “Descárgalo ya”, “El mejor”, etc.

En tus capturas actuales:

- **Diario:** hora 1:49, batería 40 % → hay que usar barra “limpia” (batería llena, señal llena).
- **Ajustes:** 12:13, 62 %, señal no llena, icono de notificación; además se ve **“API Debug Settings”** y la **URL de la API** → no es buena imagen para la ficha (información interna y aspecto de debug).
- **Momento de oración (x2):** 12:13, 71 % → misma idea: barra con niveles máximos y sin notificaciones.

---

## Cómo resolverlo

### Opción A (recomendada): Modo demostración y volver a capturar

1. En el **móvil o emulador**: **Ajustes → Opciones de desarrollador → Modo demostración** (o “Demo mode”). Actívalo.
2. En el modo demostración suele poder fijarse: **hora** (p. ej. 9:41), **batería** al 100 %, **señal** y **Wi‑Fi** al máximo, **sin notificaciones**.
3. Abre la app y **toma de nuevo** las capturas (Diario, Momento de oración, Inicio/pedidos, etc.) con esa barra ya “limpia”.
4. **Resolución:** captura en **1080×1920** (vertical). En emulador: resolución del AVD 1080×1920; en físico, que la captura no se redimensione a menor tamaño.
5. **No uses** la pantalla de Ajustes que muestra “API Debug Settings” y la URL de la API como captura de la ficha. Sustitúyela por otra pantalla (p. ej. lista de pedidos, configuración de recordatorios sin URL, etc.) o no la subas.

### Opción B: Editar las capturas que ya tienes

1. Abre cada PNG en un editor (Photoshop, GIMP, etc.).
2. **Sobre la barra de estado** (unos 70–90 px desde arriba en 1080×1920):
   - Pinta una franja negra o del mismo color que tu barra, y/o
   - Pega una “barra limpia” (hora 9:41, batería 100 %, señal y Wi‑Fi llenos, sin iconos de notificación). Puedes crear una sola imagen de barra y reutilizarla.
3. Guarda como **PNG o JPEG 24 bits**, sin transparencia si Play lo pide así. Mantén **1080×1920** (o al menos 1080 px en el lado corto).
4. Para la captura de **Ajustes**: no la subas o reemplázala por otra que no muestre URL ni “API Debug Settings”.

---

## Checklist antes de subir

- [ ] Al menos **4 capturas** de teléfono.
- [ ] Resolución mínima **1080 px** en el lado corto (ideal **1080×1920** en vertical).
- [ ] Relación de aspecto **9:16** (vertical) o **16:9** (horizontal).
- [ ] Barra de estado con **batería y señal al máximo**, sin notificaciones, hora neutra (p. ej. 9:41).
- [ ] Ninguna captura con **“API Debug Settings”** ni URL de la API en pantalla.
- [ ] Sin marcos de dispositivo, sin eslóganes que ocupen >20 %, sin “Descárgalo ya” / “El mejor”, etc.
- [ ] Texto alternativo por captura (en Play Console, al subir cada imagen).

---

## Pantallas recomendadas para las 4+ capturas

1. **Inicio / lista de pedidos de oración** (si aplica).
2. **Momento de oración** (una de las que ya tienes, con barra corregida).
3. **Diario** (entradas recientes, con barra corregida).
4. **Crear pedido o nueva entrada** (formulario).
5. **Recordatorios o Ajustes “limpios”** (sin URL ni opciones de debug).

Con esto cumples los requisitos y las recomendaciones de Play y las imágenes “quedan bien” para la ficha.
