# Métricas de backend, servicios y API para el administrador

Objetivo: que el admin tenga información **accionable** sobre salud del sistema, uso real del producto y posibles problemas, sin exponer PII.

---

## 1. Lo que ya existe (API + uso)

| Origen | Métrica | Dónde |
|--------|--------|------|
| **usageService.getUsageStats** | totalUsers, totalRecords, totalBlobBytes, syncsToday, bytesPushedToday, bytesPulledToday, byDay (DAU, bytes/día) | `GET /v1/admin/stats?days=N` |
| **health** | status, database (connected/error) | `GET /v1/health` |

El dashboard web consume `/admin/stats` (proxy a la API). Falta ampliar **backend y servicios** para dar más valor.

---

## 2. Métricas a construir (por capa)

### 2.1 Backend / base de datos (agregados desde Prisma/SQL)

Ideas que se pueden servir desde la API, leyendo solo de la BD (sin nuevo middleware de request).

| Métrica | Descripción | Valor para el admin | Fuente de datos |
|--------|-------------|---------------------|------------------|
| **Registros por estado** | Conteo de `Record` por `status`: PENDING, IN_PROCESS, ANSWERED | Ver carga de “pedidos de oración” pendientes vs respondidos | `records` |
| **Registros por tipo** | Conteo por `Record.type` (p. ej. prayer_request, journal_entry) | Saber qué tipo de contenido genera más uso | `records` |
| **Reminders activos** | Conteo de `Reminder` con `isEnabled = true` | Volumen de recordatorios programados | `reminders` |
| **Answers / testimonios en período** | Conteo de `Answer` por día o total en últimos N días | Ver si la función “respuesta a oración” se usa | `answers` |
| **Nuevos usuarios por día** | `users` agrupados por `created_at` (día) en últimos N días | Crecimiento y efectividad de captación | `users` |
| **Última actividad de sync** | Máximo `day` en `usage_logs` o “hace X horas” desde el último push/pull | Saber si la API se usa “ahora” o hace días | `usage_logs` |
| **Usuarios con DEK** | Conteo de usuarios con fila en `wrapped_dek` | Cuántos tienen búnker configurado (E2EE) | `wrapped_dek` |

**Forma de exponer:** Ampliar el contrato de `GET /v1/admin/stats` (o añadir `GET /v1/admin/stats/detail`) con un bloque opcional, por ejemplo `product: { recordsByStatus, recordsByType, remindersActive, answersCount, newUsersByDay, usersWithDek, lastSyncActivity }`. Todo agregado, sin IDs ni emails.

---

### 2.2 Servicios / aplicación (eventos que ya ocurren)

Métricas que se pueden generar **registrando eventos** en los servicios existentes (auth, sync, etc.) y luego agregando en la BD o en memoria.

| Métrica | Dónde registrar | Qué guardar (sin PII) | Valor para el admin |
|--------|------------------|-----------------------|----------------------|
| **Logins exitosos vs fallidos** | `auth/google`, `auth/login`, `auth/refresh` | Por día: éxito / fallo (y opcionalmente motivo: invalid_token, user_banned, etc.) | Detectar problemas de login o ataques de fuerza bruta |
| **Refresh tokens revocados** | `auth/logout` o revocación explícita | Contador por día (o por hora si se quiere) | Ver si hay oleadas de cierres de sesión |
| **Sync: push/pull por resultado** | Dentro del handler de sync | Por día: push ok, push conflict, pull ok, pull vacío | Calidad del sync y conflictos |
| **Errores 4xx/5xx por ruta** | Middleware global o por ruta | Agregado por ruta (o grupo: auth, sync, admin) y código HTTP | Priorizar correcciones y ver degradación |

**Forma de exponer:**  
- Opción A: tabla(s) de “eventos agregados” (p. ej. `admin_metrics_log`: día, metric_key, value_json) que un cron o el mismo request rellene; luego `GET /v1/admin/stats` (o un nuevo endpoint) lee de ahí.  
- Opción B: contadores en memoria por día que se vuelcan a un store (Redis/DB) y se leen en el endpoint de stats.  
Lo importante es no loguear PII; solo contadores o códigos de error agregados.

---

### 2.3 API / HTTP (request-level)

Métricas que requieren **observar cada request** (middleware o hook en Fastify).

| Métrica | Qué capturar | Valor para el admin |
|--------|--------------|---------------------|
| **Requests por ruta (o grupo)** | Ruta o grupo (auth, sync, admin, health) y método | Ver qué partes del API se usan más |
| **Latencia** | p50/p95/p99 por ruta o grupo | Detectar endpoints lentos o degradación |
| **Códigos HTTP** | Conteo de 2xx, 4xx, 5xx por ruta o grupo | Salud global y dónde hay errores |
| **Rate limit hits** | Cuántas veces se rechazó por límite por ruta o IP (agregado) | Detectar abuso o límites mal configurados |

**Forma de construir:**  
- Middleware que al final del request incremente contadores (por ruta, status code, tal vez bucket de latencia).  
- Los contadores pueden ser en memoria por ventana (p. ej. último minuto / última hora) y/o persistirse por día en una tabla o en el mismo `admin_metrics_log`.  
- Exponer en un endpoint de solo admin, por ejemplo `GET /v1/admin/metrics/requests?period=1h` (o incluir un resumen en `admin/stats`).

---

## 3. Priorización sugerida

| Prioridad | Qué construir | Esfuerzo | Impacto |
|-----------|----------------|----------|---------|
| **Alta** | Agregados de BD: registros por estado, por tipo, nuevos usuarios por día, última actividad de sync | Bajo (solo consultas y ampliar respuesta de stats) | El admin entiende uso real del producto y si hay “vida” reciente |
| **Alta** | Respuesta “última actividad”: último sync en la API (timestamp o “hace X h”) | Bajo | Saber si el sistema está en uso “ahora” |
| **Media** | Contadores de auth: logins ok/ko por día (sin detalle de usuario) | Medio (registro en auth + agregado) | Detectar problemas de login o abuso |
| **Media** | Errores por ruta/código (agregado en middleware) | Medio | Priorizar fallos y degradación |
| **Baja** | Latencia p50/p95 por ruta | Mayor (middleware + almacenar percentiles) | Afinar rendimiento |
| **Baja** | Rate limit hits agregados | Bajo si ya hay rate limit | Refinar límites y ver abuso |

---

## 4. Resumen

- **Backend/BD:** Ampliar estadísticas agregadas (registros por estado/tipo, reminders activos, answers, nuevos usuarios, usuarios con DEK, última actividad de sync) y exponerlas en el mismo endpoint de stats o en uno de “detalle”.
- **Servicios:** Registrar eventos agregados (auth ok/ko, revocaciones, resultado de sync) sin PII y exponerlos en el panel vía un endpoint de admin.
- **API:** Middleware de métricas por request (conteo por ruta/código, opcionalmente latencia y rate limit hits) y endpoint de solo admin para leer resúmenes.

Con esto el administrador pasa de “solo totales y DAU” a tener **información valiosa** sobre producto (qué se usa, pedidos pendientes, testimonios), salud (última actividad, errores por ruta) y seguridad (logins fallidos, rate limits), sin tocar privacidad.
