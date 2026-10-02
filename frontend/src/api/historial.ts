import { apiClient } from "./client";
import type { HistorialEntry } from "../types";

export async function miHistorial(): Promise<HistorialEntry[]> {
  const { data } = await apiClient.get<HistorialEntry[]>("/api/historial/mio");
  return data;
}

/** Para un médico (solo si ya tuvo un turno con ese paciente) o un admin. */
export async function historialDePaciente(pacienteId: number): Promise<HistorialEntry[]> {
  const { data } = await apiClient.get<HistorialEntry[]>(`/api/historial/paciente/${pacienteId}`);
  return data;
}
