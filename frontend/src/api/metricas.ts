import { apiClient } from "./client";
import type { Metricas } from "../types";

export async function obtenerMetricas(): Promise<Metricas> {
  const { data } = await apiClient.get<Metricas>("/api/metricas");
  return data;
}
