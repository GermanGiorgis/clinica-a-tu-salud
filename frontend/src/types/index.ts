export type Rol = "PACIENTE" | "MEDICO" | "ADMIN";

export type EstadoTurno = "RESERVADO" | "CONFIRMADO" | "ATENDIDO" | "AUSENTE" | "CANCELADO";

export interface Usuario {
  email: string;
  nombre: string;
  apellido: string;
  rol: Rol;
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
}

export interface Disponibilidad {
  fecha: string;
  horariosLibres: string[];
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
}

export interface MotivoCancelacion {
  id: number;
  nombre: string;
  descripcion: string | null;
}

export interface Patologia {
  id: number;
  nombre: string;
  especialidad: Especialidad;
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

export interface Consulta {
  id: number;
  turnoId: number;
  notas: string | null;
  creadaEn: string;
  diagnosticos: Diagnostico[];
  recetas: Receta[];
}

export interface HistorialEntry {
  turnoId: number;
  inicio: string;
  medicoNombre: string;
  consultorioNombre: string;
  consulta: Consulta;
}
