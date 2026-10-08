import { apiClient } from "./client";
import type { Especialidad } from "../types";

/** Catálogo público: hace falta antes de iniciar sesión para elegir con quién atenderse. */
export async function listarEspecialidades(): Promise<Especialidad[]> {
  const { data } = await apiClient.get<Especialidad[]>("/api/especialidades");
  return data;
}
