# Análisis forense: HTTP 405 en GET https://www.pr4y.cl/api/admin/login

## Hecho observado

- **URL:** `https://www.pr4y.cl/api/admin/login`
- **Respuesta:** HTTP ERROR 405 (Method Not Allowed)
- **Contexto:** Usuario abre la URL en el navegador (barra de direcciones o enlace).

## Cadena causal

1. **Petición:** Al escribir la URL y pulsar Enter (o al seguir un enlace), el navegador envía **GET** a ese recurso.
2. **Recurso:** En Next.js App Router, la ruta es `app/api/admin/login/route.ts`.
3. **Métodos exportados:** El handler exporta **POST** (flujo de Google con credential) y **GET** (redirigir a `/admin/login`).
4. **Si solo existiera POST:** Next.js respondería 405 a cualquier GET. Eso es lo que se ve en la captura si el **deploy en producción no incluye el handler GET** (código antiguo o caché de despliegue).

## Estado del código (repo)

En `apps/web/app/api/admin/login/route.ts`:

- **GET:** definido; redirige a `/admin/login` (302). Evita 405 cuando alguien visita la URL directamente.
- **POST:** definido; recibe `credential` de Google, valida y redirige a `/admin`.

Si el código desplegado en Vercel es el actual, GET no debería devolver 405.

## Conclusiones

| Causa probable | Acción |
|----------------|--------|
| Deploy en Vercel no incluye el último código (sin GET) | Hacer **Redeploy** del proyecto en Vercel o comprobar que el último commit está desplegado. |
| Caché de navegador o CDN sirviendo respuesta 405 antigua | Hard refresh (Ctrl+Shift+R) o probar en ventana de incógnito. |
| Caché de Vercel (build anterior) | En Vercel → Deployments → último deployment → "Redeploy" sin caché si está disponible. |

## Comportamiento esperado tras corrección

- **GET** `https://www.pr4y.cl/api/admin/login` → **302** → Location: `https://www.pr4y.cl/admin/login` (página del formulario).
- **POST** `https://www.pr4y.cl/api/admin/login` (con body `credential`) → lógica de login → 303 a `/admin` con cookie.

## Verificación rápida

Tras redeploy, en el navegador:

1. Abrir `https://www.pr4y.cl/api/admin/login`.
2. Debe redirigir a `https://www.pr4y.cl/admin/login` (sin pantalla de error 405).
