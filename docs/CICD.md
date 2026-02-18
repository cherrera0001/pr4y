# CI/CD – The Prayer Flow

Resumen del flujo de ramas y pipelines para PR4Y (entorno controlado, sin hotfixes que rompan firma/paquete).

## Ramas

| Rama      | Uso                    | Despliegue / resultado      |
|-----------|------------------------|-----------------------------|
| **main**  | Producción             | pr4y.cl, Google Play        |
| **dev**   | Integración            | Versión .dev, pruebas       |
| **feat/**, **fix/** | Trabajo por tarea | Se fusionan a **dev**       |

Nada se fusiona a **main** sin haber pasado por **dev** y por el CI.

## Workflows

### 1. Dev Validation (`.github/workflows/dev-validation.yml`)

- **Disparo:** PR hacia la rama **dev**.
- **Qué hace:**
  - Ejecuta `scripts/pre-flight-check.sh` (lint, typecheck API, búsqueda de STRESS TEST y delay(2000)).
  - Ejecuta `scripts/ci-sanitization.sh` (strings prohibidos tipo script:/javascript:/on*= y comprobación de tipos en el esquema de sync).
  - Lint + typecheck de la API y lint de Android.
- **Objetivo:** Validar que todo PR a **dev** cumple pre-flight y sanitización antes de merge.

### 2. Deploy API – Railway (`.github/workflows/deploy-railway.yml`)

- **Disparo:** **Push a main** (típicamente tras merge de **dev** → **main**).
- **Filtro de paths:** Solo corre si hay cambios en `apps/api/**` o `packages/**`. Cambios solo en `apps/web` o solo en `apps/mobile-android` no disparan este workflow.
- **Qué hace:** typecheck y build de la API; paso opcional de deploy con Railway CLI si está configurado `RAILWAY_TOKEN`.
- **Nota:** Si el servicio está conectado por GitHub en Railway, el deploy puede hacerse automáticamente al push a main; el step de CLI es opcional.

### 3. CI / CI Lite (PR a main)

- **Disparo:** PR hacia **main**.
- **Qué hace:** Lint y typecheck de la API, pre-flight en línea (ci-lite), lint de Android.
- **Separación Web/Mobile:** El redeploy de la API está controlado por **Deploy API – Railway** (solo cuando cambian API o packages). Los jobs de Android en CI se ejecutan en cada PR a main; si en el futuro se quiere evitar ejecutar Android cuando solo cambie web, se puede añadir un path filter (p. ej. con `dorny/paths-filter`).

## Secretos (GitHub)

- **RAILWAY_TOKEN** (opcional): Token de cuenta de Railway para deploy por CLI. Si usas solo la integración GitHub de Railway, no es necesario.
- **RAILWAY_PROJECT_ID** / **RAILWAY_SERVICE_ID** (opcionales): Si usas CLI, para enlazar proyecto y servicio.

## Crear la rama `dev`

Si aún no existe la rama **dev**:

```bash
git checkout main
git pull
git checkout -b dev
git push -u origin dev
```

En GitHub: Settings → Branches → Add rule para **main**: exigir que los PRs vengan de **dev** (o de una rama protegida) y que pasen el CI antes de merge.
