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

const MENSAJE_POR_DEFECTO = "Ocurrió un error. Probá de nuevo.";

const SIN_CONEXION = "No se pudo conectar con el servidor. Revisá tu conexión a internet; si estás conectado, el servicio puede estar fuera de línea por un rato. Probá de nuevo en unos minutos.";
const DEMORA = "El servidor tardó demasiado en responder. Probá de nuevo en unos minutos.";

function cuerpoDe(err: unknown): CuerpoError | undefined {
  if (!isAxiosError(err)) return undefined;
  const data: unknown = err.response?.data;
  return data && typeof data === "object" ? (data as CuerpoError) : undefined;
}

/** La causa cuando la falla no la explicó el servidor con un { error }: sin conexión, demora o un fallo interno. */
function causaTecnica(err: unknown): string | undefined {
  if (!isAxiosError(err)) return undefined;
  if (!err.response) {
    return err.code === "ECONNABORTED" || err.code === "ETIMEDOUT" ? DEMORA : SIN_CONEXION;
  }
  const estado = err.response.status;
  // 502/503/504 los pone el proxy o el balanceador cuando el servidor no contesta.
  if (estado === 502 || estado === 503 || estado === 504) return SIN_CONEXION;
  if (estado >= 500) return `El servidor tuvo un problema al procesar el pedido (error ${estado}). Probá de nuevo en unos minutos; si sigue, avisale a la clínica.`;
  return undefined;
}

/**
 * El texto de una falla para mostrar en pantalla. Los controllers devuelven {"error": "..."} o, en validaciones,
 * {"error": "...", "errores": {...}}; si no hay respuesta del servidor (caído, sin internet, demora) se dice eso
 * en lugar de un "algo salió mal" que no deja saber qué hacer. {@code fallback} encabeza el mensaje cuando la
 * pantalla sabe qué intentaba ("No se pudo cargar la lista de médicos").
 */
export function extraerError(err: unknown, fallback = MENSAJE_POR_DEFECTO): string {
  const data = cuerpoDe(err);
  if (data?.error && data.errores) {
    const detalles = Object.entries(data.errores)
      .map(([campo, mensaje]) => `${ETIQUETAS_CAMPO[campo] ?? campo}: ${mensaje}`)
      .join(", ");
    return `${data.error} (${detalles})`;
  }
  if (data?.error) {
    return data.error;
  }
  const causa = causaTecnica(err);
  if (causa) {
    return fallback === MENSAJE_POR_DEFECTO ? causa : `${fallback.replace(/[.:\s]+$/, "")}. ${causa}`;
  }
  return fallback;
}

/** Los errores de validación de un 400, por campo ({ email: "tiene que ser..." }), o null si la falla no es de ese tipo. */
export function erroresPorCampo(err: unknown): Record<string, string> | null {
  const errores = cuerpoDe(err)?.errores;
  return errores && Object.keys(errores).length > 0 ? errores : null;
}

/** "tiene que ser..." → "Tiene que ser...": el servidor manda los mensajes de campo en minúscula. */
export function capitalizar(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
