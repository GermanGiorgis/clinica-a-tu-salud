import { isAxiosError } from "axios";

interface CuerpoError {
  error?: string;
  errores?: Record<string, string>;
}

/** Cómo se llama cada campo de la API para quien lo está llenando (y no sabe qué es "fechaNacimiento"). */
const ETIQUETAS_CAMPO: Record<string, string> = {
  nombre: "Nombre",
  apellido: "Apellido",
  email: "Email",
  password: "Contraseña",
  telefono: "Teléfono",
  dni: "DNI",
  pacienteDni: "DNI del paciente",
  fechaNacimiento: "Fecha de nacimiento",
  matricula: "Matrícula",
  especialidadIds: "Especialidades",
  motivoConsulta: "Motivo de la consulta",
  motivoCancelacionId: "Motivo de la cancelación",
  inicio: "Fecha y hora",
  medicoId: "Médico",
  descripcion: "Descripción",
  desde: "Desde",
  hasta: "Hasta",
  numeroAfiliado: "Número de afiliado",
  obraSocialId: "Obra social",
  vigenteDesde: "Vigente desde",
  vigenteHasta: "Vigente hasta",
  puntaje: "Puntaje",
  comentario: "Comentario",
  notas: "Notas",
  diagnosticos: "Diagnósticos",
  items: "Ítems",
  importe: "Importe",
  medioPago: "Medio de pago",
  rol: "Rol",
};

/** Los controllers devuelven {"error": "..."} o, en validaciones, {"error": "...", "errores": {...}}. */
export function extraerError(err: unknown, fallback = "Ocurrió un error. Probá de nuevo."): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as CuerpoError | undefined;
    if (data?.error && data.errores) {
      const detalles = Object.entries(data.errores)
        .map(([campo, mensaje]) => `${ETIQUETAS_CAMPO[campo] ?? campo}: ${mensaje}`)
        .join(", ");
      return `${data.error} (${detalles})`;
    }
    if (data?.error) {
      return data.error;
    }
  }
  return fallback;
}
