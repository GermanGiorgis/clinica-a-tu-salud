import { apiClient } from "./client";
import type { Turno } from "../types";

export interface CrearTurnoDatos {
  medicoId: number;
  /** YYYY-MM-DDTHH:mm:ss */
  inicio: string;
  motivoConsulta?: string;
}

export async function crearTurno(datos: CrearTurnoDatos): Promise<Turno> {
  const { data } = await apiClient.post<Turno>("/api/turnos", datos);
  return data;
}

export async function misTurnos(): Promise<Turno[]> {
  const { data } = await apiClient.get<Turno[]>("/api/turnos/mios");
  return data;
}

export async function cancelarTurno(id: number, motivoCancelacionId: number): Promise<Turno> {
  const { data } = await apiClient.patch<Turno>(`/api/turnos/${id}/cancelar`, { motivoCancelacionId });
  return data;
}

export async function confirmarTurno(id: number): Promise<Turno> {
  const { data } = await apiClient.patch<Turno>(`/api/turnos/${id}/confirmar`);
  return data;
}
