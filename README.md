# Clínica

Sistema web de gestión de turnos y atención médica. Los pacientes reservan
online, los médicos administran su agenda y cargan la historia clínica, y el
personal administrativo coordina el día a día de la clínica.

**Estado:** en desarrollo. Fase 1: autenticación con roles y reserva de turnos.

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
| Paciente | Busca médicos por especialidad, reserva y cancela turnos, consulta su historial y recetas |
| Médico | Ve su agenda, atiende turnos, carga consulta, diagnóstico y receta |
| Asistente | Ve la agenda general, gestiona turnos de pacientes (por teléfono o presencial) |
| Admin | Administra usuarios y catálogos (especialidades, consultorios, horarios) |

## Stack previsto

- **Backend:** Java 21, Spring Boot, Spring Data JPA, Spring Security (JWT), PostgreSQL, Flyway
- **Frontend:** React + TypeScript (Vite)

## Estructura

```
backend/    API REST (Spring Boot)
frontend/   Aplicación web (React)
docs/       Documentación y diagramas
```

## Documentación

- [Modelo de datos y reglas de negocio](docs/modelo-datos.md)
