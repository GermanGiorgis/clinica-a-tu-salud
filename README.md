# Clínica

Sistema web de gestión de turnos y atención médica. Los pacientes reservan
online, los médicos administran su agenda y cargan la historia clínica, y el
personal administrativo coordina el día a día de la clínica.

**Estado:** el modelo completo está implementado: autenticación con roles,
reserva de turnos, atención clínica (consulta, diagnóstico, receta,
historial), cobertura (obra social y afiliación) y complementos (estudios,
encuesta de satisfacción y facturación simplificada), un panel de métricas para
el admin, una identidad visual propia y tests automáticos en el backend y en
el frontend, con integración continua en GitHub Actions. Pasó por una auditoría
de QA y seguridad cuyos hallazgos críticos están corregidos (ver
[Seguridad](#seguridad)); lo que sigue pendiente figura en
[Limitaciones conocidas](#limitaciones-conocidas).

## Origen

El proyecto nace de un trabajo práctico grupal de la facultad (Programación III,
2025), en el que cada integrante desarrolló una entidad de un CRUD para una
clínica, sin llegar a construir una aplicación utilizable.

Esta es una **reescritura completa desde cero**: modelo de datos con relaciones
reales, autenticación con roles, reglas de negocio para los turnos y una
interfaz web. No reutiliza código del trabajo original, solo la idea del dominio.

## Roles

| Rol | Qué hace |
|---|---|
| Paciente | Busca médicos por especialidad o por patología, reserva y cancela sus turnos, consulta su historial y recetas |
| Médico | Ve y gestiona su propia agenda (reserva, confirma, cancela, marca ausente), atiende turnos, carga consulta, diagnóstico y receta, y consulta el historial de los pacientes que tienen un turno con él (ver [Seguridad](#seguridad)) |
| Admin | Administra usuarios y catálogos, gestiona la agenda de cualquier médico igual que el propio médico, verifica la identidad de los pacientes que se registran, desactiva cuentas, ve el registro de accesos a historias clínicas y el panel de métricas |

## Stack

- **Backend:** Java 21, Spring Boot 4, Spring Data JPA, Spring Security (JWT), PostgreSQL, Flyway
- **Frontend:** React 19 + TypeScript (Vite)

## Estructura

```
backend/    API REST (Spring Boot)
frontend/   Aplicación web (React)
docs/       Documentación y diagramas
design-system/   Decisiones de diseño visual (paleta, tipografía, componentes)
```

## Cómo correrlo

Requisitos: Java 21, Node 20+ y PostgreSQL.

**1. Base de datos y configuración local**

```
copy backend\src\main\resources\application-local.properties.example backend\src\main\resources\application-local.properties
```

Completá `spring.datasource.password` y `jwt.secret` en ese archivo (está
ignorado por git; el ejemplo explica cómo generar un secreto). Después creá el
rol y las bases (`clinica` y `clinica_test`) una sola vez:

```
powershell -ExecutionPolicy Bypass -File backend\db\crear-db-local.ps1
```

En lugar del archivo local también se pueden usar las variables de entorno
`DB_PASSWORD` y `JWT_SECRET`. Flyway crea el esquema al arrancar.

**2. Backend** (puerto 8080)

```
cd backend
./mvnw spring-boot:run
```

En el primer arranque, y solo si todavía no existe ningún admin, el backend crea
uno. Con el archivo local copiado del ejemplo (que activa `SEED_DEMO_ADMIN`) es
una cuenta **solo de demostración**: `admin@clinica.test` / `admin1234`. Desde ahí
se dan de alta médicos, catálogos y horarios. Esa cuenta no existe a menos que se
active explícitamente: en un entorno real se definen `ADMIN_EMAIL` y `ADMIN_PASSWORD`
(ver [Seguridad](#seguridad)).

**3. Frontend** (puerto 5176)

```
cd frontend
npm install
npm run dev
```

## Tests

**Backend** (necesita PostgreSQL con la base `clinica_test`, que crea el script de arriba, y la misma contraseña configurada):

```
cd backend
./mvnw test
```

No usa H2 a propósito: las reglas que impiden reservar dos veces el mismo horario son restricciones propias de Postgres (índice único parcial y `EXCLUDE` con `btree_gist`), y H2 no las soporta.

| Tipo | Qué cubre |
|---|---|
| Unitarios de servicios (Mockito) | Reglas de negocio y permisos de cada servicio: reserva y disponibilidad (por intervalos, con la hora de la clínica), cancelación con anticipación mínima, estados del turno, cambios de horario con turnos reservados, consulta/receta/estudios, encuesta, facturación, cobertura, historial y su auditoría, verificación de identidad, límite de intentos de login, primer admin, métricas y los catálogos |
| Integración HTTP (MockMvc + Postgres real) | Filtro JWT, `SecurityConfig` (qué es público, qué exige login y qué exige rol), formato de errores de la API, login/registro, política de contraseñas, el flujo de reserva completo, los escenarios de ataque (leer un historial ajeno, suplantar un DNI, fuerza bruta, cuentas desactivadas) y las consultas de agregación de las métricas |
| Concurrencia real (hilos y commits de verdad) | Dos reservas simultáneas del mismo turno o del mismo paciente, cancelar mientras el médico marca ausente, doble clic en facturar y registros simultáneos con el mismo email: ninguna carrera deja la agenda inconsistente ni termina en un 500 |
| Consultas SQL | Que un listado haga las mismas consultas con 2 filas que con 15 (sin N+1), medido con las estadísticas de Hibernate |
| Esquema (`@DataJpaTest` + Postgres real) | Que las entidades coincidan con las migraciones y que la base rechace turnos que se solapan |

**Frontend** (Vitest + Testing Library, no necesita backend ni base):

```
cd frontend
npm test
```

Cubre la sesión (login, registro, logout y recuperación al recargar), el interceptor que manda el token, qué ve cada rol en el menú, qué rol puede entrar a cada ruta, y el comportamiento de las pantallas principales: reserva de turnos, mis turnos, agenda del médico, historial, métricas y búsqueda de médicos por patología. Las llamadas a la API se simulan.

Además se verifica el tipado con `npx tsc -b` y el lint con `npm run lint`.

**Integración continua:** `.github/workflows/ci.yml` corre todo lo anterior en cada push y pull request (el backend contra un Postgres de servicio).

## Seguridad

El sistema maneja datos de salud, así que antes de publicarlo se le hizo una auditoría de QA y
seguridad (con ataques reales contra la API, no solo lectura del código) y se corrigió lo que
encontró. Lo que quedó:

- **Historial clínico.** Un médico solo lo ve si tiene con el paciente un turno vigente o atendido
  *que no creó él mismo* y la identidad del paciente está verificada: reservar a nombre de un
  paciente por DNI no alcanza. Todo acceso de un médico o un admin queda en un registro que solo se
  agrega, consultable por el admin.
- **Registro público.** No revela si un DNI o un email pertenece a un paciente, y no deja que
  alguien se quede con el DNI de otra persona: las cuentas nuevas nacen sin identidad verificada
  y el personal las verifica comparando con el documento.
- **Contraseñas y fuerza bruta.** Mínimo 10 caracteres y máximo 72 bytes. Tras 5 intentos fallidos
  para un email (o 30 desde una IP) en 15 minutos, el login responde 429 aunque llegue la contraseña
  correcta. Las altas de cuenta se limitan por IP.
- **Cuentas.** El admin puede desactivar una cuenta comprometida y el efecto es inmediato. No existe
  ningún admin con contraseña conocida de antemano.
- **Consistencia.** Restricciones `EXCLUDE` en la base impiden turnos solapados de un médico o de un
  paciente aun con pedidos simultáneos; control optimista (`@Version`) evita que dos cambios
  simultáneos sobre un turno se pisen; un horario con turnos activos no se puede borrar ni cambiar.
- **Errores.** Siempre `{"error": "..."}`, nunca el SQL ni un stack trace; los choques entre pedidos
  simultáneos son 409, no 500.
- **Hora.** Las reglas usan la zona horaria de la clínica y no la del servidor.

### Variables de entorno para un despliegue real

| Variable | Para qué | Obligatoria |
|---|---|---|
| `DB_PASSWORD` | Contraseña del rol de la base | Sí |
| `JWT_SECRET` | Secreto para firmar los tokens (largo y aleatorio) | Sí: sin él el backend no arranca |
| `ADMIN_EMAIL` y `ADMIN_PASSWORD` | Primer usuario administrador (la contraseña tiene que cumplir la política y no se escribe en el log) | Sí, la primera vez |
| `CORS_ALLOWED_ORIGIN` | Origen del frontend | Sí, si no es `http://localhost:5176` |
| `ZONA_HORARIA` | Zona de la clínica | No (por defecto `America/Argentina/Buenos_Aires`) |
| `SEED_DEMO_ADMIN` | Crea el admin de demostración | **No activar fuera de tu máquina** |

## Limitaciones conocidas

Lo que la auditoría marcó y sigue pendiente, dicho sin maquillaje:

- **Sin verificación de email.** Cualquiera puede registrarse con un email ajeno, y el 409 de
  "ese email ya existe" permite averiguar si una dirección tiene cuenta (se mitiga con el límite
  de altas por IP, pero no se elimina). Resolverlo bien necesita enviar correos.
- **Sin reprogramación ni avisos.** Reprogramar es cancelar y volver a reservar, y si un médico
  cancela el paciente no se entera: no hay mails ni SMS. Tampoco hay cancelación masiva por
  ausencia de un médico.
- **Los turnos vencidos no se cierran solos.** Un turno `RESERVADO` o `CONFIRMADO` del pasado
  queda así hasta que alguien lo resuelva (el médico puede verlo ahora desde el filtro "Día" de la
  agenda), y eso sesga el ausentismo de las métricas.
- **Sin rol de recepción.** Quien atienda el mostrador tiene que ser `ADMIN`, y un admin ve todos
  los historiales (con registro de acceso, pero los ve). Un rol intermedio con agenda y sin acceso
  clínico sería lo correcto.
- **Un médico puede reservar y cargar consultas a nombre de cualquier paciente verificado.** Es
  lo que hace falta para trabajar en un mostrador, y queda auditado (cada turno guarda quién lo
  creó), pero no hay un control previo.
- **El token vive en `localStorage`** y dura 24 horas. React no tiene puntos de XSS en este
  proyecto, pero una cookie `httpOnly` con CSRF y una política CSP serían más robustas.
- **El límite de intentos está en memoria**: sirve para una sola instancia del backend. Con varias
  habría que moverlo a un almacén compartido. Detrás de un proxy inverso hay que configurar
  `server.forward-headers-strategy`, o todos los usuarios parecen venir de la misma IP.
- **Datos en reposo sin cifrar** y listados sin paginar (hoy no hace falta; con miles de turnos por
  médico sí).

## Documentación

- [Modelo de datos y reglas de negocio](docs/modelo-datos.md)
