# Modelo de datos y reglas de negocio

Este documento fija el modelo antes de escribir código. Parte del dominio del
trabajo práctico original (turnos, médicos, pacientes, recetas...) pero
corrige lo que allí quedó sin resolver: relaciones reales entre tablas, tipos
de datos adecuados y reglas de negocio explícitas.

## Principios

- **Relaciones reales.** Toda referencia entre tablas es una clave foránea
  (`@ManyToOne` en JPA), no un `Long` suelto.
- **Tipos correctos.** Dinero en `decimal` (`BigDecimal`), fechas y horas con
  `LocalDate` / `LocalTime` / `LocalDateTime`, números de afiliado como texto
  (pueden tener ceros a la izquierda).
- **Enums para conjuntos cerrados** con lógica asociada (estado del turno, rol,
  medio de pago). **Tablas para catálogos** que el administrador edita
  (especialidades, consultorios, medicamentos).
- **Sin borrado físico** de registros con historia clínica: usuarios y médicos
  se desactivan (`activo = false`).
- **Nunca se exponen entidades**: la API trabaja con DTOs.
- **Datos de demostración ficticios.** El sistema maneja información de salud;
  el repositorio solo contiene datos inventados.
- Nombres de dominio en español (`Turno`, `Medico`); tablas en `snake_case`.

## Diagrama entidad-relación

```mermaid
erDiagram
    USUARIO ||--o| PACIENTE : "tiene perfil"
    USUARIO ||--o| MEDICO : "tiene perfil"
    USUARIO ||--o{ TURNO : "registra"
    MEDICO }o--o{ ESPECIALIDAD : "ejerce"
    ESPECIALIDAD ||--o{ PATOLOGIA : "clasifica"
    MEDICO ||--o{ HORARIO_ATENCION : "define"
    CONSULTORIO ||--o{ HORARIO_ATENCION : "aloja"
    PACIENTE ||--o{ TURNO : "reserva"
    MEDICO ||--o{ TURNO : "atiende"
    CONSULTORIO ||--o{ TURNO : "sede de"
    MOTIVO_CANCELACION |o--o{ TURNO : "justifica"
    PACIENTE ||--o{ AFILIACION : "posee"
    OBRA_SOCIAL ||--o{ AFILIACION : "cubre"
    AFILIACION |o--o{ TURNO : "se usa en"
    TURNO ||--o| CONSULTA : "origina"
    CONSULTA ||--o{ DIAGNOSTICO : "registra"
    CONSULTA ||--o{ RECETA : "emite"
    RECETA ||--|{ ITEM_RECETA : "contiene"
    MEDICAMENTO ||--o{ ITEM_RECETA : "se prescribe en"
    CONSULTA ||--o{ ORDEN_ESTUDIO : "solicita"
    ESTUDIO ||--o{ ORDEN_ESTUDIO : "define"
    ORDEN_ESTUDIO ||--o| RESULTADO_ESTUDIO : "produce"
    TURNO ||--o| ENCUESTA : "se evalua en"
    TURNO ||--o| FACTURA : "se cobra en"
    FACTURA ||--|{ ITEM_FACTURA : "detalla"
    USUARIO ||--o{ ACCESO_HISTORIAL : "accede"
    PACIENTE ||--o{ ACCESO_HISTORIAL : "es consultado en"

    USUARIO {
        bigint id PK
        string email UK
        string password_hash
        string rol "PACIENTE, MEDICO, ADMIN"
        string nombre
        string apellido
        string telefono
        boolean activo
        timestamp creado_en
    }
    PACIENTE {
        bigint id PK
        bigint usuario_id FK, UK
        string dni "UK solo entre verificados"
        date fecha_nacimiento
        boolean identidad_verificada "la clinica la comprobo en persona"
    }
    ACCESO_HISTORIAL {
        bigint id PK
        bigint usuario_id FK "quien accedio (medico o admin)"
        bigint paciente_id FK "de quien es el historial"
        timestamp accedido_en
    }
    MEDICO {
        bigint id PK
        bigint usuario_id FK, UK
        string matricula UK
    }
    ESPECIALIDAD {
        bigint id PK
        string nombre UK
        string descripcion
    }
    PATOLOGIA {
        bigint id PK
        string nombre UK
        bigint especialidad_id FK
    }
    CONSULTORIO {
        bigint id PK
        string nombre UK
        string ubicacion
        int piso
    }
    HORARIO_ATENCION {
        bigint id PK
        bigint medico_id FK
        bigint consultorio_id FK
        string dia_semana "DayOfWeek"
        time hora_inicio
        time hora_fin
        int duracion_turno_min
        date vigente_desde
        date vigente_hasta "opcional"
    }
    TURNO {
        bigint id PK
        bigint paciente_id FK
        bigint medico_id FK
        bigint consultorio_id FK
        bigint creado_por_id FK "usuario que lo registro"
        bigint afiliacion_id FK "opcional, fase 2"
        bigint motivo_cancelacion_id FK "solo si esta cancelado"
        timestamp inicio
        timestamp fin
        string estado "EstadoTurno"
        string motivo_consulta
        timestamp creado_en
        bigint version "control optimista de concurrencia"
    }
    MOTIVO_CANCELACION {
        bigint id PK
        string nombre UK
        string descripcion
    }
    OBRA_SOCIAL {
        bigint id PK
        string nombre UK
        string telefono
    }
    AFILIACION {
        bigint id PK
        bigint paciente_id FK
        bigint obra_social_id FK
        string numero_afiliado
        string plan
        date vigente_desde
        date vigente_hasta "opcional"
    }
    CONSULTA {
        bigint id PK
        bigint turno_id FK, UK
        text notas
        timestamp creada_en
    }
    DIAGNOSTICO {
        bigint id PK
        bigint consulta_id FK
        string descripcion
        string codigo_cie10 "opcional"
    }
    MEDICAMENTO {
        bigint id PK
        string nombre
        string presentacion
    }
    RECETA {
        bigint id PK
        bigint consulta_id FK
        string observaciones
        timestamp emitida_en
    }
    ITEM_RECETA {
        bigint id PK
        bigint receta_id FK
        bigint medicamento_id FK
        string dosis
        string frecuencia
        int duracion_dias
    }
    ESTUDIO {
        bigint id PK
        string nombre UK
        string tipo
    }
    ORDEN_ESTUDIO {
        bigint id PK
        bigint consulta_id FK
        bigint estudio_id FK
        string estado "EstadoOrden"
        string indicaciones
        timestamp emitida_en
    }
    RESULTADO_ESTUDIO {
        bigint id PK
        bigint orden_estudio_id FK, UK
        text resultado
        timestamp cargado_en
    }
    ENCUESTA {
        bigint id PK
        bigint turno_id FK, UK
        int puntaje "1 a 5"
        string comentario
        timestamp creada_en
    }
    FACTURA {
        bigint id PK
        bigint turno_id FK, UK
        decimal total
        string medio_pago "MedioPago"
        timestamp emitida_en
    }
    ITEM_FACTURA {
        bigint id PK
        bigint factura_id FK
        string descripcion
        decimal importe
    }
```

`ADMIN` no tiene tabla propia: es un valor de `USUARIO.rol` y no guarda datos
específicos de su rol.

`PATOLOGIA` es un catálogo que edita el admin: cada síntoma/patología apunta a
una `ESPECIALIDAD`. Un paciente que no sabe a qué especialista ir elige "qué
le pasa" y el sistema le filtra los médicos de esa especialidad (ver regla 13).
Es solo una ayuda de navegación, no una restricción: el paciente igual puede
elegir cualquier médico directamente.

## Fases

| Fase | Estado | Alcance | Entidades |
|---|---|---|---|
| 1. Turnos | ✅ hecha | Login con roles, catálogos, agenda, derivación por patología y reserva de turnos | `Usuario`, `Paciente`, `Medico`, `Especialidad`, `Patologia`, `Consultorio`, `HorarioAtencion`, `Turno`, `MotivoCancelacion` |
| 2. Atención clínica | ✅ hecha | Consulta, diagnóstico, receta e historial | `Consulta`, `Diagnostico`, `Medicamento`, `Receta`, `ItemReceta` |
| 2b. Cobertura | ✅ hecha | Obra social y afiliación del paciente | `ObraSocial`, `Afiliacion` |
| 3. Complementos | ✅ hecha | Estudios, encuestas y cobros | `Estudio`, `OrdenEstudio`, `ResultadoEstudio`, `Encuesta`, `Factura`, `ItemFactura` |
| 4. Métricas | ✅ hecha | Panel de indicadores de la clínica para el admin | Ninguna entidad nueva: se calcula con consultas agregadas |

La fase 2b agrega `ObraSocial` (catálogo que edita el admin) y `Afiliacion`
(cada paciente gestiona las propias: alta, listado y baja). `turno.afiliacion_id`
es opcional: al reservar, un paciente puede indicar con qué afiliación propia
se atiende ese turno concreto; si no indica ninguna, el turno queda sin
cobertura asociada ("particular").

La fase 3 implementada cubre: `OrdenEstudio` (1:1 con `ResultadoEstudio`,
cuelga de una `Consulta` igual que `Receta`), `Encuesta` (1:1 con `Turno`,
la carga una sola vez el propio paciente después de ser atendido) y una
`Factura` simplificada (1:1 con `Turno`, con sus `ItemFactura`; el total lo
calcula el servidor a partir de los ítems, nunca el que mande el cliente).
El panel de métricas (fase 4) es solo para el `ADMIN` (`GET /api/metricas`) y no guarda nada: la base cuenta y suma con `GROUP BY` y el servicio solo completa los ceros y calcula los porcentajes. Informa turnos por estado, pacientes distintos atendidos, ausentismo (ausentes sobre atendidos + ausentes: los cancelados y los pendientes no cuentan), satisfacción promedio de las encuestas, facturación total y por medio de pago, y una fila por médico con sus atendidos, ausentes, satisfacción y facturado. Si todavía no hay datos para calcular un porcentaje o un promedio, devuelve `null` en lugar de un cero que parezca real.

## Estados del turno

`EstadoTurno` es un enum con transiciones controladas por el servicio, no una
tabla editable.

```mermaid
stateDiagram-v2
    [*] --> RESERVADO : paciente, médico o admin reserva
    RESERVADO --> CONFIRMADO : confirma
    RESERVADO --> CANCELADO : cancela
    CONFIRMADO --> ATENDIDO : el médico carga la consulta
    CONFIRMADO --> AUSENTE : el paciente no se presenta
    CONFIRMADO --> CANCELADO : cancela
    ATENDIDO --> [*]
    AUSENTE --> [*]
    CANCELADO --> [*]
```

| Transición | Quién puede |
|---|---|
| Reservar | Paciente (para sí mismo); médico o admin, a nombre de un paciente (por DNI) |
| Confirmar | Paciente (el propio); médico (de su agenda); admin (cualquiera) |
| Cancelar | Paciente (con anticipación mínima); médico (de su agenda) y admin (cualquiera), sin esa restricción |
| Marcar ausente | Médico (de su agenda), admin |
| Atendido | Médico del turno, al cargar la consulta |

## Reglas de negocio

**Agenda y turnos**

1. Los turnos salen de la grilla del médico: el `inicio` debe caer dentro de un
   `HorarioAtencion` de ese día y alinearse con `duracion_turno_min`. Los
   horarios libres se calculan (horarios menos turnos activos); no se guardan.
2. Un médico no puede tener dos turnos activos (no cancelados) que se pisen en
   el tiempo. Se garantiza **en la base de datos** con una restricción
   `EXCLUDE` (extensión `btree_gist`) sobre `(medico_id, rango [inicio, fin))`
   donde `estado <> 'CANCELADO'`, además de la validación en el servicio:
   así dos reservas simultáneas no pueden duplicar el turno, y tampoco
   solaparse si el horario cambió de duración. Los horarios libres se
   calculan por intersección de intervalos, no comparando horas de inicio.
3. Un paciente no puede tener dos turnos activos superpuestos (misma
   restricción `EXCLUDE`, sobre `paciente_id`): ni siquiera pidiendo dos
   médicos a la vez.
4. No se reserva en el pasado ni más allá de la ventana de 60 días; la
   disponibilidad tampoco ofrece horarios fuera de esa ventana.
5. El paciente solo puede cancelar con una anticipación mínima configurable
   (por ejemplo, 2 horas). El médico (sus propios turnos) y el admin
   (cualquiera) pueden cancelar siempre, sin esa restricción. Toda
   cancelación exige un `MotivoCancelacion`.
6. Los horarios de un mismo médico no se pisan entre sí, y un consultorio no
   puede tener dos médicos en el mismo horario. Un horario no se puede
   eliminar ni modificar si algún turno activo (`RESERVADO` o `CONFIRMADO`,
   de hoy en adelante) dejaría de encajar en él (otro día, consultorio,
   duración o fuera de rango): primero hay que cancelar o reprogramar esos
   turnos. Ampliar la franja manteniendo la grilla sí se permite.
6b. Dos personas que modifican el mismo turno a la vez (por ejemplo, el
   paciente cancela mientras el médico lo marca ausente) no se pisan en
   silencio: `Turno` tiene control optimista (`@Version`) y la segunda
   operación recibe un 409.
6c. Todas las reglas que dependen de "ahora" usan la zona horaria de la
   clínica (`clinica.zona-horaria`), no la del servidor.
7. Un paciente puede elegir una `Patologia` al buscar turno; el sistema la
   traduce a su `Especialidad` y filtra los médicos que la ejercen. Es solo
   un filtro de ayuda: el paciente igual puede elegir cualquier médico
   directamente, sin pasar por una patología.

**Atención clínica**

8. La `Consulta` solo existe para turnos confirmados y es 1:1 con el turno.
   Crearla pasa el turno a `ATENDIDO`. Solo el médico del turno (o un admin)
   la carga; no se edita después. Una `Receta` cuelga de una Consulta ya
   cargada y la emite esa misma persona.
9. El historial del paciente **no es una tabla**: se calcula a partir de sus
   turnos `ATENDIDO` (vía la Consulta de cada uno) — no incluye estudios
   todavía, eso es fase 3.

**Acceso a los datos (mínimo privilegio)**

10. El **paciente** solo ve y gestiona sus propios turnos.
11. El **médico** gestiona (reserva, confirma, cancela, marca ausente) los
    turnos de su propia agenda, y ve el historial completo de un paciente
    solo si tiene con él un turno `RESERVADO`, `CONFIRMADO` o `ATENDIDO`
    **que no creó él mismo** y la identidad del paciente está verificada.
    La condición del creador importa: un médico puede reservar a nombre de
    cualquier paciente por DNI, y si eso alcanzara, cualquier médico podría
    leer cualquier historial reservando (y cancelando) un turno. Un turno
    cancelado o ausente no habilita nada.
12. El **admin** gestiona usuarios, catálogos, y puede gestionar la agenda y
    los turnos de cualquier médico — las mismas acciones que tiene el médico
    sobre la suya. También verifica identidades, activa o desactiva cuentas
    y consulta la auditoría y las métricas.

**Identidad, cuentas y auditoría**

13. Cualquiera puede registrarse como paciente con cualquier DNI, así que el
    DNI **no se valida contra otros pacientes al registrarse** (avisar "ese
    DNI ya existe" revelaría quién se atiende en la clínica). La cuenta nace
    con `identidad_verificada = false`. El personal compara en persona los
    datos con el documento y verifica la cuenta; recién entonces el DNI pasa
    a ser único (índice parcial) y es posible reservar a su nombre por DNI y
    que un médico vea su historial. Los pacientes dados de alta por un admin
    nacen verificados.
14. Cada acceso de un médico o un admin al historial de otra persona queda
    registrado (`acceso_historial`: quién, a quién, cuándo). El registro solo
    se agrega, y el admin lo consulta.
15. Contraseñas de al menos 10 caracteres y como máximo 72 bytes (límite de
    BCrypt). Tras 5 intentos fallidos de login para un email, o 30 desde una
    IP, en 15 minutos, se responde 429 aunque llegue la contraseña correcta;
    las altas de cuenta se limitan a 10 por IP por hora.
16. El primer `ADMIN` se crea desde `ADMIN_EMAIL` y `ADMIN_PASSWORD`; el
    admin de demostración existe solo si se activa explícitamente
    (`SEED_DEMO_ADMIN`), y es solo para desarrollo local.
17. Un admin puede desactivar cualquier cuenta menos la suya. El efecto es
    inmediato: el filtro JWT consulta la base en cada pedido.

## Cambios respecto al trabajo original

| Original | Acá |
|---|---|
| Todas las referencias como `Long` (`pacienteId`, `medicoId`...) | Claves foráneas reales con `@ManyToOne` |
| `EstadoTurno` como tabla con CRUD | Enum con máquina de estados |
| `Asistente` como entidad, con su propio CRUD | Eliminado: hacía lo mismo que `Admin`, que ya puede gestionar todo el sistema de turnos |
| `HistorialPaciente` como tabla de eventos | Se calcula desde consultas, recetas y estudios |
| `Medico.especialidad` como `String` | Relación N:M con `Especialidad` |
| `Turno` con `fecha` y `hora` separadas | `inicio` y `fin` como `LocalDateTime` |
| `Afiliacion`: `obraSocialId` como `String`, `numeroAfiliado` como `Long` | FK a `ObraSocial`; número como texto |
| `Factura.monto` como `Double` | `decimal` / `BigDecimal` |
| `HorarioAtencion` con horas como `String` | `LocalTime`, más consultorio y duración del turno |
| `ResultadoEstudio.fechaCarga` como `LocalTime` | `timestamp` |
| Entidades referenciadas pero inexistentes (`Consulta`, `Estudio`, `MedioPago`) | `Consulta` y `Estudio` como entidades; `MedioPago` como enum |
| Sin login, validaciones, DTOs ni tests | Spring Security con JWT, Bean Validation, DTOs y tests |

## Fuera de alcance por ahora

Bloqueos de agenda (vacaciones, licencias), recordatorios por mail, subida de
archivos de resultados, integraciones con sistemas de facturación oficiales.
Ver también las limitaciones conocidas del README (reprogramación y avisos,
cierre automático de turnos vencidos, rol de recepción, verificación de email).
