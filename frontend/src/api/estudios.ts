import { apiClient } from "./client";
import type { Estudio } from "../types";

export async function listarEstudios(): Promise<Estudio[]> {
  const { data } = await apiClient.get<Estudio[]>("/api/estudios");
  return data;
}
