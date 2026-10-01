import { apiClient } from "./client";
import type { Factura, MedioPago } from "../types";

export interface CrearFacturaDatos {
  medioPago: MedioPago;
  items: { descripcion: string; importe: number }[];
}

export async function crearFactura(turnoId: number, datos: CrearFacturaDatos): Promise<Factura> {
  const { data } = await apiClient.post<Factura>(`/api/turnos/${turnoId}/factura`, datos);
  return data;
}

export async function obtenerFactura(turnoId: number): Promise<Factura> {
  const { data } = await apiClient.get<Factura>(`/api/turnos/${turnoId}/factura`);
  return data;
}
