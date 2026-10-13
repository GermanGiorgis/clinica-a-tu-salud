export type Rol = "PACIENTE" | "MEDICO" | "RECEPCIONISTA" | "ADMIN";

export type EstadoTurno = "RESERVADO" | "CONFIRMADO" | "ATENDIDO" | "AUSENTE" | "CANCELADO";

export interface Usuario {
  email: string;
  nombre: string;
  apellido: string;
  rol: Rol;
}

/** Una cuenta tal como la lista el admin (activa o de baja). */
export interface UsuarioListado {
  id: number;
  email: string;
  nombre: string;
  apellido: string;
  telefono: string | null;
  rol: Rol;
  activo: boolean;
  perfilId: number | null;
}

export interface Especialidad {
  id: number;
  nombre: string;
  descripcion: string | null;
}

export interface Medico {
  id: number;
  nombre: string;
  apellido: string;
  matricula: string;
  especialidades: Especialidad[];
  /** Falso = dado de baja: no se ve ni se puede reservar, salvo para el personal del mostrador. */
  activo: boolean;
}

export interface Disponibilidad {
  fecha: string;
  horariosLibres: string[];
  /** Por qué no hay horarios cuando el día está cerrado (feriado, ausencia del médico); null en un día normal. */
  aviso?: string | null;
}

export interface Turno {
  id: number;
  medicoId: number;
  medicoNombre: string;
  pacienteId: number;
  pacienteNombre: string;
  consultorioId: number;
  consultorioNombre: string;
  inicio: string;
  fin: string;
  estado: EstadoTurno;
  motivoConsulta: string | null;
  motivoCancelacionNombre: string | null;
  obraSocialNombre: string | null;
}

export interface MotivoCancelacion {
  id: number;
  nombre: string;
  descripcion: string | null;
}

export interface Patologia {
  id: number;
  nombre: string;
  /** Las especialidades que atienden este síntoma (una o varias). */
  especialidades: Especialidad[];
}

export interface Diagnostico {
  id: number;
  descripcion: string;
  codigoCie10: string | null;
}

export interface Medicamento {
  id: number;
  nombre: string;
  presentacion: string | null;
}

export interface ItemReceta {
  id: number;
  medicamentoId: number;
  medicamentoNombre: string;
  dosis: string;
  frecuencia: string;
  duracionDias: number;
}

export interface Receta {
  id: number;
  consultaId: number;
  observaciones: string | null;
  emitidaEn: string;
  items: ItemReceta[];
}

export type EstadoOrdenEstudio = "SOLICITADA" | "COMPLETADA";

export interface ResultadoEstudio {
  id: number;
  resultado: string;
  cargadoEn: string;
}

export interface OrdenEstudio {
  id: number;
  estudioId: number;
  estudioNombre: string;
  estado: EstadoOrdenEstudio;
  indicaciones: string | null;
  emitidaEn: string;
  resultado: ResultadoEstudio | null;
}

export interface Consulta {
  id: number;
  turnoId: number;
  notas: string | null;
  creadaEn: string;
  diagnosticos: Diagnostico[];
  recetas: Receta[];
  ordenesEstudio: OrdenEstudio[];
}

export interface Estudio {
  id: number;
  nombre: string;
  tipo: string;
}

export interface Encuesta {
  id: number;
  puntaje: number;
  comentario: string | null;
  creadaEn: string;
}

export type MedioPago = "EFECTIVO" | "TARJETA" | "TRANSFERENCIA" | "OBRA_SOCIAL";

export interface ItemFactura {
  id: number;
  descripcion: string;
  importe: number;
}

export interface Factura {
  id: number;
  turnoId: number;
  total: number;
  medioPago: MedioPago;
  emitidaEn: string;
  items: ItemFactura[];
}

export interface HistorialEntry {
  turnoId: number;
  inicio: string;
  medicoNombre: string;
  consultorioNombre: string;
  consulta: Consulta;
}

export interface ObraSocial {
  id: number;
  nombre: string;
  telefono: string | null;
}

export interface Afiliacion {
  id: number;
  obraSocialId: number;
  obraSocialNombre: string;
  numeroAfiliado: string;
  plan: string | null;
  vigenteDesde: string;
  vigenteHasta: string | null;
}

export interface ConteoPorEstado {
  estado: EstadoTurno;
  cantidad: number;
}

export interface MontoPorMedioPago {
  medioPago: MedioPago;
  cantidad: number;
  total: number;
}

export interface MetricaMedico {
  medicoId: number;
  nombre: string;
  turnosAtendidos: number;
  turnosAusentes: number;
  puntajePromedio: number | null;
  encuestas: number;
  facturado: number;
}

export interface Metricas {
  totalTurnos: number;
  pacientesAtendidos: number;
  tasaAusentismo: number | null;
  puntajePromedio: number | null;
  totalEncuestas: number;
  facturacionTotal: number;
  turnosPorEstado: ConteoPorEstado[];
  facturacionPorMedioPago: MontoPorMedioPago[];
  medicos: MetricaMedico[];
}

export interface PacienteAVerificar {
  id: number;
  usuarioId: number;
  nombre: string;
  apellido: string;
  email: string;
  telefono: string | null;
  dni: string;
  fechaNacimiento: string;
  identidadVerificada: boolean;
  cuentaActiva: boolean;
}

export interface AccesoHistorial {
  id: number;
  usuarioId: number;
  usuarioEmail: string;
  usuarioNombre: string;
  usuarioRol: Rol;
  pacienteId: number;
  pacienteNombre: string;
  accedidoEn: string;
}

/** Feriado, cierre de la clínica (medicoId null) o ausencia de un médico, cargado por anticipado. */
export interface DiaNoLaborable {
  id: number;
  desde: string;
  hasta: string;
  descripcion: string;
  medicoId: number | null;
  medicoNombre: string | null;
}

/** Turno pendiente que cae dentro de un feriado o una ausencia; trae el teléfono para poder avisarle al paciente. */
export interface TurnoAfectado {
  id: number;
  inicio: string;
  estado: EstadoTurno;
  medicoId: number;
  medicoNombre: string;
  pacienteNombre: string;
  pacienteTelefono: string | null;
  consultorioNombre: string;
}
