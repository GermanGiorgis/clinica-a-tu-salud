import { apiClient } from "./client";
import type { Disponibilidad, Medico } from "../types";

export async function listarMedicos(): Promise<Medico[]> {
  const { data } = await apiClient.get<Medico[]>("/api/medicos");
  return data;
}

export async function obtenerMedico(id: number): Promise<Medico> {
  const { data } = await apiClient.get<Medico>(`/api/medicos/${id}`);
  return data;
}

/** fecha en formato YYYY-MM-DD */
export async function obtenerDisponibilidad(medicoId: number, fecha: string): Promise<Disponibilidad> {
  const { data } = await apiClient.get<Disponibilidad>(`/api/medicos/${medicoId}/disponibilidad`, {
    params: { fecha },
  });
  return data;
}
