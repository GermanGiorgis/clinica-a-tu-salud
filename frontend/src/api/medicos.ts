import { apiClient } from "./client";
import type { Disponibilidad, Medico } from "../types";

/**
 * `incluirInactivos` solo lo respeta el backend para admin y recepción (para elegir la agenda de un médico
 * dado de baja y cancelarle los turnos que le quedaron); para cualquier otra persona se ignora.
 */
export async function listarMedicos(especialidadId?: number, incluirInactivos?: boolean): Promise<Medico[]> {
  const params = { ...(especialidadId ? { especialidadId } : {}), ...(incluirInactivos ? { incluirInactivos: true } : {}) };
  const { data } = await apiClient.get<Medico[]>("/api/medicos", { params: Object.keys(params).length ? params : undefined });
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
