import { apiClient } from "./client";
import type { Turno } from "../types";

export interface CrearTurnoDatos {
  medicoId: number;
  /** YYYY-MM-DDTHH:mm:ss */
  inicio: string;
  motivoConsulta?: string;
  /** Solo la usa un médico o admin reservando a nombre de otra persona. */
  pacienteDni?: string;
}

export async function crearTurno(datos: CrearTurnoDatos): Promise<Turno> {
  const { data } = await apiClient.post<Turno>("/api/turnos", datos);
  return data;
}

export async function misTurnos(): Promise<Turno[]> {
  const { data } = await apiClient.get<Turno[]>("/api/turnos/mios");
  return data;
}

/** medicoId es obligatorio para un admin; un médico puede omitirlo (usa el suyo). */
export async function agendaMedico(medicoId?: number, fecha?: string): Promise<Turno[]> {
  const { data } = await apiClient.get<Turno[]>("/api/turnos/agenda", {
    params: { medicoId, fecha },
  });
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

export async function marcarAusente(id: number): Promise<Turno> {
  const { data } = await apiClient.patch<Turno>(`/api/turnos/${id}/marcar-ausente`);
  return data;
}
