import { apiClient } from "./client";
import type { DiaNoLaborable, TurnoAfectado } from "../types";

/** Solo ADMIN y RECEPCIONISTA. Devuelve los feriados y ausencias que todavía no terminaron. */
export async function listarDiasNoLaborables(): Promise<DiaNoLaborable[]> {
  const { data } = await apiClient.get<DiaNoLaborable[]>("/api/dias-no-laborables");
  return data;
}

export interface NuevoDiaNoLaborable {
  desde: string;
  hasta: string;
  descripcion: string;
  /** Sin médico = feriado o cierre de toda la clínica. */
  medicoId?: number;
}

export async function crearDiaNoLaborable(dia: NuevoDiaNoLaborable): Promise<DiaNoLaborable> {
  const { data } = await apiClient.post<DiaNoLaborable>("/api/dias-no-laborables", dia);
  return data;
}

export async function eliminarDiaNoLaborable(id: number): Promise<void> {
  await apiClient.delete(`/api/dias-no-laborables/${id}`);
}

/** Turnos pendientes que todavía no empezaron y caen dentro del período. */
export async function turnosAfectados(id: number): Promise<TurnoAfectado[]> {
  const { data } = await apiClient.get<TurnoAfectado[]>(`/api/dias-no-laborables/${id}/turnos-afectados`);
  return data;
}

/** Cancela en bloque los turnos afectados con un mismo motivo; devuelve cuántos fueron. */
export async function cancelarTurnosAfectados(id: number, motivoCancelacionId: number): Promise<number> {
  const { data } = await apiClient.post<{ cancelados: number }>(`/api/dias-no-laborables/${id}/cancelar-turnos`, {
    motivoCancelacionId,
  });
  return data.cancelados;
}
