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

En **Settings → Environment Variables** del proyecto:

| Variable | Valor | Uso |
|----------|--------|-----|
| `NEXT_PUBLIC_API_URL` | `https://pr4yapi-production.up.railway.app/v1` | API del SaaS (Railway). El frontend ya tiene este valor por defecto en código; conviene fijarlo aquí para producción. |
| `ADMIN_SECRET_KEY` | (opcional) Tu secreto para la puerta `/admin/gate` | Si no se define, el panel admin solo exige JWT de admin. |

## Después del deploy

- **Landing:** https://pr4y.cl
- **Admin:** https://pr4y.cl/admin (protegido por gate y JWT)
- Las visitas a `*.vercel.app` se redirigen con 308 a **pr4y.cl** (middleware).

## Resumen de arquitectura

- **pr4y.cl** → Cloudflare (registro A) → Vercel (landing + admin).
- **API:** https://pr4yapi-production.up.railway.app/v1 (Railway); el frontend en Vercel consume esta URL.
- **App Android:** habla directo a Railway; no depende de la web.
