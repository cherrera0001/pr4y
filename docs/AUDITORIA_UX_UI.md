# Auditoría UX/UI — PR4Y Mobile (Lead UX/UI Designer)

## Resumen ejecutivo

Auditoría de flujo de usuario, accesibilidad y "paz mental" (Calm Tech) en las pantallas y componentes de UI de la app PR4Y. Se evalúan empty states, feedback de sincronización, accesibilidad (tamaños de fuente y contraste para oración en poca luz) y fricción en el flujo de autenticación/E2EE.

---

## 1. Empty States

### Estado actual

- **HomeScreen (Pedidos):** Si `requests` está vacío, el usuario ve solo la barra superior, los botones "Diario" / "Buscar" y una **LazyColumn vacía** sin mensaje. No hay ilustración ni copy que invite a crear el primer pedido.
- **JournalScreen (Diario):** Si no hay entradas, la lista está vacía sin ningún empty state. El FAB "Nueva entrada" está presente pero no hay contexto motivador.
- **NewJournalScreen / NewEditScreen:** No aplica empty state (son formularios).

### Evaluación

- **Motivador vs frustrante:** Actualmente **neutro-frustrante**. Una lista vacía sin explicación puede interpretarse como "no hay nada" o "algo falló". Para una app de oración, el primer uso debería transmitir calma e invitación.

### Recomendaciones (implementadas en código)

- **HomeScreen:** Mostrar un empty state cuando `requests.isEmpty()`: icono sutil (ej. manos o vela), título tipo "Aún no hay pedidos" y subtítulo "Añade el primero y compártelo con quien quieras que ore por ti", CTA "Crear pedido".
- **JournalScreen:** Empty state cuando no hay entradas: "Tu diario está vacío", "Escribe tu primera reflexión o gratitud" y botón "Nueva entrada" destacado.

---

## 2. Feedback de Sync (Calm Tech)

### Estado actual

- **HomeScreen:** Si hay ítems en outbox se muestra un **Card** con "X cambio(s) sin sincronizar" y botón "Sincronizar". No hay indicador cuando todo está al día.
- **SettingsScreen:** Solo "Sincronizar ahora" con snackbar de éxito/error. No hay indicador de estado habitual (protegido/sincronizado).
- **SyncWorker:** Ejecuta en segundo plano; no hay superficie en UI para "último sync ok" o "sync fallando repetidamente" de forma sutil.

### Evaluación

- El usuario **no** tiene una señal clara de "tus datos están protegidos y sincronizados" sin hacer nada. Solo ve algo cuando hay pendientes o cuando entra a Ajustes y pulsa "Sincronizar ahora". Eso puede generar duda ("¿se subió lo mío?").
- Pop-ups: No hay pop-ups agresivos; los snackbars son aceptables. Objetivo: **reducir** la necesidad de que el usuario piense en sync.

### Recomendaciones (implementadas en código)

- **Indicador sutil "Protegido y Sincronizado":** En HomeScreen y SettingsScreen, cuando outbox está vacío y último sync fue éxito: pequeño indicador (icono escudo + check o texto breve) en la parte superior o junto a la app bar, sin robar foco.
- **Errores no intrusivos:** Si el SyncWorker falla repetidamente, no mostrar diálogo bloqueante. Mostrar una **barra o chip sutil** tipo "Sincronización pausada. Comprueba tu conexión." con opción "Reintentar" o que desaparezca al tener conexión/sync exitoso. Persistir estado de "último error" en SyncStateDao para mostrarlo solo cuando sea relevante.

---

## 3. Accesibilidad (poca luz / noche)

### Estado actual

- **Tema:** Material 3 con `Pr4yTheme` (dark/light según sistema). Dark: `background = #121212`, `surface = #1E1E1E`, `primary = #7986CB`, `secondary = #81C784`. Light: fondos claros y primarios estándar.
- **Tipografía:** Se usa `MaterialTheme.typography` sin escalado ni tamaños mínimos explícitos (bodyMedium, titleMedium, labelSmall, etc.). No hay `Typography` personalizado con tamaños aumentados para lectura nocturna.

### Evaluación

- **Contraste:** En dark theme, texto sobre `surface`/`background` suele cumplir WCAG para texto normal. Conviene revisar etiquetas pequeñas (`labelSmall`) en fondos `surfaceVariant` o `outline`.
- **Tamaño de fuente:** Para oración en condiciones de poca luz, `bodyMedium` puede quedarse justo. Un cuerpo de texto ligeramente mayor (ej. 16sp mínimo) y un título que escale mejor mejora la "paz mental" sin parecer técnico.

### Recomendaciones (implementadas en código)

- **Theme:** Definir `Typography` con `bodyLarge` como cuerpo principal para pantallas de lectura (diario, detalle de pedido) y asegurar que el tamaño base de body sea al menos ~16sp. Mantener `labelSmall` pero con contraste suficiente en dark (onSurfaceVariant).
- **Contraste:** Revisar que en dark theme `onSurfaceVariant` y `outline` tengan ratio ≥ 4.5:1 sobre el fondo. Material 3 ya suele cumplirlo; se documenta en esta auditoría para futuras variantes.

---

## 4. Fricción en Auth (registro y llaves E2EE)

### Estado actual

- **LoginScreen:** Email, contraseña (mín. 8), botón "Entrar" / "Registrarse" y toggle "Crear cuenta" / "Ya tengo cuenta". Mensajes de error genéricos ("Error ${e.code}").
- **UnlockScreen:** Primera vez: "Configurar Acceso" con passphrase, opción "Usar biometría para entrar" y botón "Configurar y Entrar". Usuario existente: "Desbloquear" con passphrase y biometría si está disponible. Textos: "Passphrase", "Usar biometría para entrar". No se explica qué es la passphrase ni por qué existe.

### Evaluación

- **Transparencia vs técnico:** Para un usuario común, términos como "Passphrase", "DEK", "Configurar Acceso" sin contexto pueden sonar técnicos. No se comunica el beneficio: "Tus oraciones y tu diario solo los ves tú; ni nosotros podemos leerlos."
- **Flujo:** El orden (registro → login → unlock con passphrase/biometría) es correcto, pero la **primera vez** que se pide la passphrase no hay una línea que explique que es la "clave de acceso a tus datos cifrados" y que la puede guardar con biometría para no escribirla cada vez.

### Recomendaciones (no implementadas en esta fase; solo auditoría)

- Añadir en **UnlockScreen** (primera vez): un subtítulo tipo "Elige una clave que solo tú conozcas. Con ella se protegen tus oraciones y tu diario en este dispositivo y en la nube."
- En **LoginScreen:** Si el error es de red o credenciales, mensajes más amigables: "Revisa tu correo y contraseña" o "Sin conexión. Comprueba la red."
- Evitar jerga ("DEK", "E2EE") en toda la UI; mantener "protegido", "cifrado", "solo tú puedes leerlo".

---

## 5. Modo offline y "Espera de Desbloqueo"

### Estado actual

- **NewJournalScreen / NewEditScreen:** Si `DekManager.getDek()` es null, en NewJournalScreen el "Guardar" depende de `dek` y no guarda (runBlocking con `if (dek == null) return`). El usuario que abre la app sin desbloquear no puede persistir una entrada de diario.

### Evaluación

- Si `tryRecoverDekSilently()` falla (ej. reinicio sin desbloqueo), el usuario puede estar "dentro" de la app pero sin DEK. Debe poder **seguir escribiendo** y guardar en un estado de "Espera de Desbloqueo" (borrador) para que al desbloquear se cifre y suba.

### Recomendaciones (implementadas en código)

- **NewJournalScreen:** Si `getDek() == null`, permitir escribir y al "Guardar" persistir el contenido como **borrador local** (ej. SharedPreferences o tabla `journal_draft`) con un mensaje: "Guardado como borrador. Desbloquea la app para proteger y sincronizar." Al desbloquear, se puede migrar el borrador a una entrada cifrada y ponerla en outbox (opcional en esta fase: al menos el usuario no pierde lo escrito).
- **NewEditScreen (pedidos):** Similar opción: si no hay DEK, guardar solo en local (RequestEntity sin outbox) y mostrar aviso sutil de que se sincronizará al desbloquear.

---

## Resumen de cambios implementados en código

| Área            | Cambio                                                                 |
|-----------------|------------------------------------------------------------------------|
| Empty states    | HomeScreen y JournalScreen con empty state motivador y CTA            |
| Feedback sync   | Indicador "Protegido y Sincronizado"; estado de error no intrusivo    |
| Accesibilidad   | Typography con body de lectura cómoda y contraste documentado          |
| SyncWorker      | Escritura de último estado (éxito/error) en SyncStateDao              |
| Modo offline    | Borrador de diario cuando DEK no está disponible                      |
| API + Android   | Metadata de conflicto en push; Pull-before-Push y manejo de rechazos  |

---

*Documento generado en el marco de la auditoría UX/UI y Fase 3 (Resiliencia y conflicto de sync).*
