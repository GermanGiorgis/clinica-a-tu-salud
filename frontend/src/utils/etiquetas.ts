import type { EstadoTurno, MedioPago } from "../types";

export const ETIQUETA_ESTADO: Record<EstadoTurno, string> = {
  RESERVADO: "Reservado",
  CONFIRMADO: "Confirmado",
  ATENDIDO: "Atendido",
  AUSENTE: "Ausente",
  CANCELADO: "Cancelado",
};

export const ETIQUETA_MEDIO_PAGO: Record<MedioPago, string> = {
  EFECTIVO: "Efectivo",
  TARJETA: "Tarjeta",
  TRANSFERENCIA: "Transferencia",
  OBRA_SOCIAL: "Obra social",
};

const formatoMoneda = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });

export function formatearMoneda(importe: number): string {
  return formatoMoneda.format(importe);
}
