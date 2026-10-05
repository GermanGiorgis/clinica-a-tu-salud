import { apiClient } from "./client";
import type { PacienteAVerificar } from "../types";

/** Solo ADMIN: cuentas de paciente que todavía esperan que la clínica verifique su identidad. */
export async function pacientesPendientes(): Promise<PacienteAVerificar[]> {
  const { data } = await apiClient.get<PacienteAVerificar[]>("/api/pacientes/pendientes");
  return data;
}

export async function verificarPaciente(id: number): Promise<PacienteAVerificar> {
  const { data } = await apiClient.patch<PacienteAVerificar>(`/api/pacientes/${id}/verificar`);
  return data;
}
