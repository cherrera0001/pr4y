# apps/web

Frontend web de PR4Y. Landing minimalista, política de privacidad y backoffice admin.

## Despliegue en Vercel (monorepo)

- **Root Directory:** en Project Settings → Root Directory = `apps/web` (evita 404).
- **Dominio canónico:** pr4y.cl. El middleware redirige `*.vercel.app` a pr4y.cl.
- **Variables (sin valores por defecto en código):** `NEXT_PUBLIC_API_URL` (obligatoria), `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` (obligatoria para Sign in with Google en /admin), `NEXT_PUBLIC_CANONICAL_HOST` (opcional, ej. pr4y.cl), `ADMIN_SECRET_KEY` (opcional, puerta /admin). Ver `VERCEL.md` en la raíz del repo.