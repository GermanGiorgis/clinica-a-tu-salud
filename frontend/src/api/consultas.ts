import { apiClient } from "./client";
import type { Consulta, Diagnostico } from "../types";

export interface CrearConsultaDatos {
  notas?: string;
  diagnosticos: Pick<Diagnostico, "descripcion" | "codigoCie10">[];
}

export async function crearConsulta(turnoId: number, datos: CrearConsultaDatos): Promise<Consulta> {
  const { data } = await apiClient.post<Consulta>(`/api/turnos/${turnoId}/consulta`, datos);
  return data;
}

export async function obtenerConsultaDeTurno(turnoId: number): Promise<Consulta> {
  const { data } = await apiClient.get<Consulta>(`/api/turnos/${turnoId}/consulta`);
  return data;
}
