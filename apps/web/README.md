# apps/web

Frontend web de PR4Y. Landing minimalista, política de privacidad y backoffice admin.

## Despliegue en Vercel (monorepo)

- **Root Directory:** en Project Settings → Root Directory = `apps/web` (evita 404).
- **Dominio canónico:** pr4y.cl. El middleware redirige `*.vercel.app` a pr4y.cl.
- **Variables:** `NEXT_PUBLIC_API_URL` (por defecto `https://pr4yapi-production.up.railway.app/v1`), y opcionalmente `ADMIN_SECRET_KEY` para proteger `/admin`.