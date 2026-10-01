import { apiClient } from "./client";
import type { Patologia } from "../types";

export async function listarPatologias(): Promise<Patologia[]> {
  const { data } = await apiClient.get<Patologia[]>("/api/patologias");
  return data;
}
