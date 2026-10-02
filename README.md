# Clínica

Sistema web de gestión de turnos y atención médica. Los pacientes reservan
online, los médicos administran su agenda y cargan la historia clínica, y el
personal administrativo coordina el día a día de la clínica.

**Estado:** el modelo completo está implementado: autenticación con roles,
reserva de turnos, atención clínica (consulta, diagnóstico, receta,
historial), cobertura (obra social y afiliación) y complementos (estudios,
encuesta de satisfacción y facturación simplificada), un panel de métricas para
el admin, una identidad visual propia y tests automáticos en el backend y en
el frontend, con integración continua en GitHub Actions.

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
| Médico | Ve y gestiona su propia agenda (reserva, confirma, cancela, marca ausente), atiende turnos, carga consulta, diagnóstico y receta, y consulta el historial de los pacientes que atendió |
| Admin | Administra usuarios y catálogos, gestiona la agenda de cualquier médico igual que el propio médico y ve el panel de métricas de la clínica |

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

En el primer arranque se crea una cuenta de administrador **solo de
demostración** (`admin@clinica.test` / `admin1234`); desde ahí se dan de alta
médicos, catálogos y horarios. Cambiala o eliminala fuera de un entorno de prueba.

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

No usa H2 a propósito: la regla que impide reservar dos veces el mismo horario es un índice único parcial de Postgres, y H2 no lo soporta.

| Tipo | Qué cubre |
|---|---|
| Unitarios de servicios (Mockito) | Reglas de negocio y permisos de cada servicio: reserva y disponibilidad, cancelación con anticipación mínima, estados del turno, consulta/receta/estudios, encuesta, facturación, cobertura, historial, métricas y los catálogos |
| Integración HTTP (MockMvc + Postgres real) | Filtro JWT, `SecurityConfig` (qué es público, qué exige login y qué exige rol), formato de errores de la API, login/registro, el flujo de reserva completo con la restricción real de la base y las consultas de agregación de las métricas |
| Esquema (`@DataJpaTest` + Postgres real) | Que las entidades coincidan con las migraciones y que la base rechace el doble turno |

**Frontend** (Vitest + Testing Library, no necesita backend ni base):

```
cd frontend
npm test
```

Cubre la sesión (login, registro, logout y recuperación al recargar), el interceptor que manda el token, qué ve cada rol en el menú, qué rol puede entrar a cada ruta, y el comportamiento de las pantallas principales: reserva de turnos, mis turnos, agenda del médico, historial, métricas y búsqueda de médicos por patología. Las llamadas a la API se simulan.

Además se verifica el tipado con `npx tsc -b` y el lint con `npm run lint`.

**Integración continua:** `.github/workflows/ci.yml` corre todo lo anterior en cada push y pull request (el backend contra un Postgres de servicio).

## Documentación

- [Modelo de datos y reglas de negocio](docs/modelo-datos.md)
