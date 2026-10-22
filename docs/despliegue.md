# Demo en línea (Vercel + Neon)

La demo pública corre en **un solo proyecto de Vercel** con dos servicios y una base **Neon** (PostgreSQL). Es un
entorno descartable: cualquiera puede escribir en él, así que una vez por día se borra todo y se vuelve a cargar.

```
navegador ──► Vercel ──► /api/*  ──► servicio "backend"  (contenedor: backend/Dockerfile) ──► Neon (Postgres)
                    └──► el resto ─► servicio "frontend" (Vite, archivos estáticos)
```

Todo se define en [`vercel.json`](../vercel.json). Como la API y el frontend comparten el mismo dominio, no hay CORS
ni hace falta saber la dirección del backend: el frontend llama a `/api/...`.

## Qué hay que saber

- **Arranque en frío.** Vercel apaga el contenedor tras 5 minutos sin tráfico (30 segundos en las vistas previas). El
  primer pedido lo vuelve a arrancar y la JVM tarda unos 30-40 segundos. En modo demostración el frontend espera hasta
  90 segundos y avisa «el servidor se está despertando» en la franja de arriba.
- **Sin estado.** El contenedor no guarda nada: todo está en Neon. El único estado en memoria es el límite de intentos,
  que se pierde al apagarse (no importa en una demo).
- **Reinicio diario.** Un cron de Vercel llama cada día a las 04:00 (hora de Mendoza, 07:00 UTC) a
  `GET /api/demo/reiniciar` con `Authorization: Bearer $CRON_SECRET`. El backend borra toda la base con Flyway
  (`clean`), la recrea (`migrate`) y vuelve a cargar los datos de demostración
  ([`ReinicioDeDemostracion`](../backend/src/main/java/com/germanggiorgis/clinica/config/ReinicioDeDemostracion.java)).
  Funciona solo con **dos** variables encendidas a la vez y con un secreto definido; en cualquier otro entorno la ruta
  responde 404.
- **Las cuentas de demostración tienen contraseña pública** (están en el README y en el login). Es a propósito: se
  borran todas las noches. Por eso la demo muestra un aviso para no ingresar datos personales reales.

## Variables de entorno (entorno *Production* del proyecto de Vercel)

| Variable | Valor | Para qué |
|---|---|---|
| `PGHOST_UNPOOLED`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` | las inyecta la integración de Neon | Conexión directa a la base |
| `DB_URL_PARAMS` | `?sslmode=require` | Neon exige SSL |
| `DB_POOL_SIZE` | `3` | Pocas conexiones: Neon gratuito tiene un tope |
| `JWT_SECRET` | secreto aleatorio largo | Firma de los tokens (sensible) |
| `PORT` | `8080` | Puerto en el que escucha el contenedor (no corre como root, no puede usar el 80) |
| `FORWARD_HEADERS_STRATEGY` | `native` | Que el límite de intentos vea la IP real y no la del proxy de Vercel |
| `SEED_DEMO_ADMIN`, `SEED_DEMO_CUENTAS`, `SEED_DEMO_MEDICOS` | `true` | Datos y cuentas de demostración |
| `DEMO_REINICIO` | `true` | Habilita el reinicio diario |
| `FLYWAY_CLEAN_DISABLED` | `false` | Segundo candado: sin esto Flyway se niega a borrar |
| `CRON_SECRET` | secreto aleatorio largo | Lo manda Vercel en cada llamada del cron (sensible) |
| `VITE_MODO_DEMO` | `true` | Aviso de demostración y botones «Entrar como…» |
| `VITE_API_URL` | `/` | El frontend llama a su propio dominio |

> `SEED_DEMO_*`, `DEMO_REINICIO` y `FLYWAY_CLEAN_DISABLED=false` son **solo para una demo descartable**. Con datos
> reales, ninguna de estas variables debe estar definida.

## Pasos

1. **Proyecto.** En Vercel: *Add New → Project* y elegir este repositorio (raíz del repositorio; Vercel lee `vercel.json`).
2. **Base de datos.** En el proyecto: *Storage → Create → Neon* (Marketplace), aceptar y conectarla al proyecto. Eso crea
   la base e inyecta las variables `PG*`.
3. **Variables.** Cargar las de la tabla de arriba (`JWT_SECRET` y `CRON_SECRET` generados al azar, por ejemplo con
   `openssl rand -hex 32`).
4. **Vistas previas.** Los PRs no deberían desplegarse contra la misma base (una migración nueva se aplicaría sobre la
   base de la demo). En *Settings → Git → Ignored Build Step* usar:
   `[ "$VERCEL_GIT_COMMIT_REF" != "main" ]` (sale con éxito = se saltea el build).
5. **Desplegar.** Cada push a `main` despliega solo. La primera vez, el backend crea el esquema con Flyway y carga los
   datos de demostración.

## Verificar

```
scripts/smoke-test.sh https://TU-DOMINIO https://TU-DOMINIO
```

(el frontend y la API comparten dominio). Para forzar un reinicio de datos a mano:
`curl -H "Authorization: Bearer $CRON_SECRET" https://TU-DOMINIO/api/demo/reiniciar`.
