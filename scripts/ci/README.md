# Scripts CI / Pre-push

Ejecuta estos checks **antes de hacer push** para no gastar minutos de GitHub Actions y detectar fallos en local.

## Escaneo de secretos (gitleaks)

Asegura que no haya credenciales ni API keys en el código.

- **Bash**: `./scripts/ci/scan-secrets.sh`
- **PowerShell**: `.\scripts\ci\scan-secrets.ps1`

Requisito: Docker. Alternativa sin Docker: instala [gitleaks](https://github.com/gitleaks/gitleaks) y ejecuta:

```bash
gitleaks detect --source . --no-git
```

## Escaneo de vulnerabilidades (Trivy)

Opcional: escanear dependencias y sistema de archivos.

- **Bash**: `./scripts/ci/scan-trivy.sh [ruta]` (por defecto raíz del repo)

Requisito: Docker.

## Checklist local antes de push

1. `pnpm run typecheck` (raíz o `pnpm run api:typecheck`)
2. `pnpm run lint` (raíz o `pnpm run api:lint`)
3. Android: `cd apps/mobile-android && ./gradlew lint` (o `gradlew.bat lint` en Windows)
4. `./scripts/ci/scan-secrets.sh` (o .ps1)

Si algo falla en local, no subas el código hasta corregirlo; así se optimiza el uso de minutos gratuitos de GitHub Actions.

## Estrategia costo $0 en GitHub

- **CI solo en PRs a `main`**: el workflow `.github/workflows/ci.yml` se ejecuta únicamente en `pull_request` hacia `main`, no en cada push a otras ramas.
- **Cache agresivo**: se cachean dependencias de pnpm y de Gradle para no descargar todo en cada run.
- **Branch protection** (configurar en GitHub): en Settings → Branches → regla para `main` puedes activar "Require status checks to pass before merging" y seleccionar los jobs de CI (API y Android). Así el merge solo se permite si el CI pasa.
