# Clínica

Sistema web de gestión de turnos y atención médica. Los pacientes reservan
online, los médicos administran su agenda y cargan la historia clínica, y el
personal administrativo coordina el día a día de la clínica.

**Estado:** fases 1, 2 y 3 hechas: autenticación con roles, reserva de turnos,
atención clínica (consulta, diagnóstico, receta, historial), cobertura (obra
social y afiliación) y complementos (estudios, encuesta de satisfacción y
facturación simplificada), con una identidad visual propia. Falta agregar
tests automáticos a la capa de servicios.

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
| Médico | Ve y gestiona su propia agenda (reserva, confirma, cancela, marca ausente), atiende turnos, carga consulta, diagnóstico y receta |
| Admin | Administra usuarios y catálogos, y puede gestionar la agenda de cualquier médico igual que el propio médico |

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
