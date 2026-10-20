# Cómo colaborar

¡Gracias por sumarte! Esta guía alcanza para levantar el proyecto, probar un cambio y proponerlo.

## 1. Levantarlo

Lo más rápido es Docker (ver el [README](README.md#probalo-en-un-minuto)):

```
docker compose up --build
```

Para desarrollar con recarga en vivo hace falta Java 21, Node 22 y PostgreSQL: seguí
[Cómo correrlo sin Docker](README.md#cómo-correrlo-sin-docker). El archivo
`application-local.properties` está ignorado por git (lleva tu contraseña de la base): no lo subas.

## 2. Trabajar un cambio

1. Actualizá `main` y creá una rama: `git switch -c lo-que-cambia`.
2. Hacé el cambio **con sus tests**. Si es una regla de negocio, probá también el caso en que se rompe.
3. Corré todo antes de subirlo:

   | Dónde | Comando |
   |---|---|
   | Backend (necesita Postgres con la base `clinica_test`) | `cd backend && ./mvnw test` |
   | Frontend | `cd frontend && npm run lint && npm test && npm run build` |
   | Todo junto, con Docker | `docker compose up -d --build && scripts/smoke-test.sh` |

4. Subí la rama y abrí un **Pull Request** a `main`. El CI corre los mismos chequeos (backend, frontend y Docker);
   tienen que quedar en verde.

## 3. Convenciones

- **Commits:** una línea de resumen en inglés, en imperativo y sin punto ("Add reprogramming to My turnos"), y un
  cuerpo corto que cuente *por qué*. Mirá `git log` como referencia.
- **Código y comentarios:** el dominio está en castellano (clases, mensajes, comentarios); escribí igual que lo que
  tenés alrededor.
- **Mensajes al usuario:** en castellano rioplatense y diciendo qué pasó y qué hacer, nunca "Ocurrió un error" a secas.
- **Base de datos:** el esquema lo crea Flyway. Un cambio es una migración **nueva** (`V13__...`): no edites las
  que ya existen.
- **Reglas de negocio:** están numeradas en [docs/modelo-datos.md](docs/modelo-datos.md). Si cambiás una, actualizá ese
  documento y el README en el mismo PR.
- **Diseño:** paleta y tipografía en [design-system/clinica/MASTER.md](design-system/clinica/MASTER.md).

## 4. Qué no hacer

- No subas contraseñas, tokens ni datos reales de personas. Las claves de `docker-compose.yml` y las cuentas de
  demostración son públicas a propósito y solo sirven para probar.
- No actives `SEED_DEMO_ADMIN`, `SEED_DEMO_CUENTAS` ni `SEED_DEMO_MEDICOS` en un entorno con datos reales.

## 5. Ideas para empezar

La lista de lo que falta está en [Limitaciones conocidas](README.md#limitaciones-conocidas): avisos al paciente,
verificación de email, cierre automático de turnos vencidos, calendario de feriados de Mendoza.
