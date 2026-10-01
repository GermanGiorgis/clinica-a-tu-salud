import { apiClient } from "./client";
import type { Encuesta } from "../types";

export async function crearEncuesta(turnoId: number, puntaje: number, comentario?: string): Promise<Encuesta> {
  const { data } = await apiClient.post<Encuesta>(`/api/turnos/${turnoId}/encuesta`, { puntaje, comentario });
  return data;
}

export async function obtenerEncuesta(turnoId: number): Promise<Encuesta> {
  const { data } = await apiClient.get<Encuesta>(`/api/turnos/${turnoId}/encuesta`);
  return data;
}
