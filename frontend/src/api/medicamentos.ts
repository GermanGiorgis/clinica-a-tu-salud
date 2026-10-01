import { apiClient } from "./client";
import type { Medicamento } from "../types";

export async function listarMedicamentos(): Promise<Medicamento[]> {
  const { data } = await apiClient.get<Medicamento[]>("/api/medicamentos");
  return data;
}
