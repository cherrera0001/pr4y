# Instrucción para agente: revisión y corrección completa de PR4Y

Usa este prompt al invocar un agente (Cursor, CLI o otro) para que revise y corrija todo lo necesario para que las apps PR4Y funcionen correctamente.

---

## Objetivo

Revisar de forma sistemática el monorepo PR4Y (API Fastify, web Next.js, app Android Kotlin) y **corregir** todos los bugs, gaps técnicos, warnings accionables y comportamientos incorrectos que encuentres, respetando las reglas en `docs/AGENTS.md` y `.cursor/rules/`.

## Alcance

1. **API (apps/api)**  
   - Rutas, servicios, Prisma, validación de entradas, rate limits, CORS, errores, tipos.  
   - Completar endpoints documentados pero no implementados (p. ej. si existe referencia a `GET/POST /v1/public/requests` en cliente y no en API, implementarlos).  
   - No registrar contenido de usuarios en logs. No persistir texto en claro.

2. **Web (apps/web)**  
   - Páginas, formularios, llamadas a la API, middleware, layout.  
   - Enlaces rotos, estados de error, accesibilidad básica.  
   - Que las rutas públicas (landing, pedir oración, términos, privacidad) funcionen sin login.

3. **Mobile Android (apps/mobile-android)**  
   - Llamadas a la API, manejo de errores y estados vacíos, hilos (no bloquear main thread con crypto/IO).  
   - Sanitización de inputs, navegación, ViewModels.  
   - No introducir dependencias o permisos innecesarios.

4. **Contrato y documentación**  
   - Coherencia entre API (OpenAPI o docs/api.md), cliente Android (ApiService) y web.  
   - Actualizar docs si añades o cambias endpoints.

## Restricciones (obligatorias)

- **Seguridad:** Nunca loguear ni persistir contenido de oraciones/notas en claro. E2EE: el servidor solo ve cifrado.  
- **Ramas:** No hacer hotfixes directos a `main`; trabajar en `feat/...` o `fix/...` y fusionar a `dev`.  
- **Sin invención:** Si falta un dato (URL, env, feature flag), usar `[PREGUNTA]` o `[SUPUESTO]` explícito en lugar de inventar.  
- **Validación:** Entradas validadas con esquemas (Zod en web, JSON Schema en API). Sanitización contra XSS e inyección.

## Criterios de “funciona correctamente”

- La API arranca, las migraciones aplican y los endpoints documentados/usados por el cliente responden (o devuelven 404/4xx coherente).  
- La web muestra las páginas públicas y, si aplica, el panel admin con auth.  
- La app Android puede iniciar sesión, sincronizar (pull/push), y las pantallas (Home, Ruleta, Diario, etc.) no bloquean el main thread ni crashean por errores no manejados.  
- No queden warnings o errores accionables en código (lint/typecheck pasan; los mensajes de sistema/MIUI que no controla la app se pueden documentar pero no “corregir” en código).

## Entregables esperados

1. Lista de ítems revisados (por app o módulo).  
2. Lista de cambios realizados (archivo + descripción breve).  
3. Si algo no se pudo corregir (p. ej. depende de env o decisión de producto), listarlo como pendiente con `[PREGUNTA]` o `[SUPUESTO]`.

## Referencias en el repo

- Reglas generales: `docs/AGENTS.md`  
- Ramas y CI/CD: `.cursor/rules/branching-cicd.mdc`  
- Gaps técnicos conocidos: `docs/REPORTE-GAPS-TECNICOS-SUPERVISION.md`  
- Requerimientos detallados (si existe): `docs/PROMPT-PR4Y-MEGA.md`

---

*Copiar y pegar el contenido de este archivo (o su path) al invocar al agente para que ejecute esta revisión.*
