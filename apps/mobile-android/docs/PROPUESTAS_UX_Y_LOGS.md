# Mejoras aplicadas y propuestas UX/Logs

## Logs (aplicado)

- **Pr4yLog**: `i`, `w`, `net`, `crypto`, `sync` solo se emiten en **DEBUG**. `e()` en release trunca el mensaje a 200 caracteres (evitar fugas).
- **RetrofitClient**: nivel de logging **NONE** en release; en DEBUG solo **HEADERS** (nunca BODY, para no volcar tokens ni respuestas).
- **NetworkModule**: mismo criterio (HEADERS en DEBUG, NONE en release).
- **Pr4yApp**: el log de arranque `PR4Y_DEBUG` solo en DEBUG.

La UI "API Debug Settings" en Ajustes ya está envuelta en `BuildConfig.DEBUG`; en builds de producción no se muestra.

---

## Tema y UX (aplicado)

- **Tema claro/oscuro**: la app sigue el tema del sistema (`isSystemInDarkTheme()`). El esquema claro tiene todos los roles de color definidos (onSurface, onBackground, etc.) para buen contraste.
- **Tipografía**: títulos con **Serif** (más cálida), cuerpo con **Default**; tamaños en `sp` para respetar el tamaño de fuente de accesibilidad del sistema.
- Si en el futuro se quiere "forzar claro/oscuro" o "tamaño de letra dentro de la app", basta con guardar una preferencia y pasarla a `Pr4yTheme(darkTheme = …)` y/o usar un factor de escala en la tipografía.

---

## Propuestas pendientes (UX)

1. **Tamaño de letra en app**: Ajustes → "Tamaño de texto" con 3 opciones (Pequeño / Normal / Grande) guardada en `SharedPreferences`/DataStore y aplicada vía `Typography` con escalado (p. ej. 0.9f / 1f / 1.15f sobre los `sp` base).
2. **Botones más móvil**: usar `Button`/`FilledTonalButton` de Material3 con altura mínima ~48.dp, `shape = RoundedCornerShape(24)` o similar, y evitar estilos tipo "link" (TextButton) para acciones principales; reservar TextButton para secundarias (Cancelar, Enlace).
3. **Contraste y modo alto contraste**: revisar `outlineVariant` y bordes en modo claro para cumplir accesibilidad; opcionalmente un toggle "Alto contraste" que refuerce bordes y diferencia onSurface/onSurfaceVariant.
4. **Evidencia**: usar las capturas en `evidencia/` para priorizar pantallas (Home, Login, Roulette, Ajustes) y ajustar espaciados y jerarquía visual.

---

## Resumen técnico

| Área        | Cambio |
|------------|--------|
| Pr4yLog    | i/w/net/crypto/sync solo DEBUG; e() truncado en release |
| RetrofitClient | Logging NONE (release) / HEADERS (DEBUG), nunca BODY |
| NetworkModule  | Idem |
| Pr4yApp    | Log arranque solo DEBUG |
| Theme      | Sistema claro/oscuro; LightColorScheme completo; Serif en títulos |
