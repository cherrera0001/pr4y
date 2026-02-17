# Deploy en Vercel (pr4y.cl — landing)

La landing y el panel admin se sirven desde Vercel. El dominio **pr4y.cl** apunta por Cloudflare a Vercel.

## 404 de plataforma: el dominio está verificado pero la raíz devuelve 404

Si en Vercel los dominios tienen el check azul y aun así **pr4y.cl** (o la raíz) devuelve **404**, el fallo es de **archivos/ruta compilada**: Vercel no está usando el directorio donde está la app Next.js.

- En un monorepo, la **raíz del repo** no contiene `app/page.tsx` ni `next.config.js`; están en **`apps/web`**.
- Si **Root Directory** está vacío o en `.`, Vercel compila desde la raíz, no detecta bien Next.js y no sirve la ruta `/` (no hay `index.html` ni app en la raíz).

### Solución (obligatoria en monorepo)

1. [Vercel Dashboard](https://vercel.com) → tu proyecto → **Settings** → **General**.
2. En **Root Directory** pulsa **Edit**.
3. Escribe exactamente: **`apps/web`** (sin barra final, sin espacios).
4. Guarda.
5. **Redeploy**: **Deployments** → menú del último deployment → **Redeploy** (o push un commit).

Así Vercel toma `apps/web` como raíz del proyecto, detecta Next.js, compila desde ahí y la ruta raíz (`app/page.tsx`) se sirve correctamente en **pr4y.cl**.

## Variables de entorno (Vercel)

En **Settings → Environment Variables** del proyecto hay que definir **al menos estas dos** para que la web y el login admin funcionen:

| Variable | Valor | Uso |
|----------|--------|-----|
| `NEXT_PUBLIC_API_URL` **o** `NEXT_PUBLIC_API_BASE_URL` | `https://pr4yapi-production.up.railway.app/v1` | URL base de la API (Railway). **Obligatoria** (basta con una de las dos). Sin ella la web no puede llamar al backend. |
| `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` | Mismo que en el backend (Railway): `xxxxx.apps.googleusercontent.com` | Cliente OAuth **Web** de Google. **Obligatoria** para "Sign in with Google" en `/admin/login`. Sin ella aparece el mensaje de configuración y no se muestra el botón. |
| `NEXT_PUBLIC_CANONICAL_HOST` | (opcional) `pr4y.cl` | Redirección de `*.vercel.app` al dominio canónico. |
| `ADMIN_SECRET_KEY` | (opcional) Tu secreto para la puerta `/admin/gate` | Si no se define, el panel admin solo exige JWT de admin. |

**Quién es administrador:** Lo define la **base de datos** (campo `User.role`: `admin` o `super_admin`). No se hardcodea en la web ni en env. Quien tenga ese rol en la API puede establecer sesión en `/admin/login`. Para tener un único admin (ej. crherrera@c4a.cl), crear o actualizar ese usuario en la BD con el rol correspondiente (script en API o panel cuando exista).

**Checklist:** Si la versión web falla o en login sale *"Falta configuración en Vercel"*:

1. Vercel → proyecto → **Settings** → **Environment Variables**.
2. **URL de la API:** Añade **`NEXT_PUBLIC_API_URL`** = `https://pr4yapi-production.up.railway.app/v1` (o la URL de tu API en Railway, con `/v1` al final).  
   - Si ya tienes `NEXT_PUBLIC_API_BASE_URL`, puedes usar el mismo valor para `NEXT_PUBLIC_API_URL`; el código acepta cualquiera de las dos, pero si el mensaje persiste tras un redeploy, añadir `NEXT_PUBLIC_API_URL` lo resuelve.
3. **Google:** Añade `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` = el **cliente Web** de Google (el mismo que usa el backend en Railway). En Google Cloud Console es el OAuth 2.0 Client ID de tipo **Web application**.
4. Marca las variables para **Production** (y Preview si usas previews).
5. **Redeploy**: Deployments → menú (⋯) del último deployment → **Redeploy** (o haz push de un commit). Las variables se inyectan en el build; sin redeploy no se aplican.

**CORS (API en Railway):** Para que el login admin en pr4y.cl pueda llamar a la API, en Railway la variable **CORS_ORIGINS** del servicio API debe incluir los orígenes web: `https://pr4y.cl` y `https://www.pr4y.cl`. Sin ellos el navegador bloqueará las peticiones a `/auth/google` o podrás ver errores de red o 405 en consola. Ver **docs/PROMPT-FIX-ADMIN-LOGIN-WEB.md** si aparecen errores de COOP o 405 en /admin/login.

## Después del deploy

- **Landing:** https://pr4y.cl
- **Admin:** https://pr4y.cl/admin (protegido por gate y JWT)
- Las visitas a `*.vercel.app` se redirigen con 308 a **pr4y.cl** (middleware).

## Resumen de arquitectura

- **pr4y.cl** → Cloudflare (registro A) → Vercel (landing + admin).
- **API:** https://pr4yapi-production.up.railway.app/v1 (Railway); el frontend en Vercel consume esta URL.
- **App Android:** habla directo a Railway; no depende de la web.
