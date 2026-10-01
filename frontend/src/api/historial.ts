import { apiClient } from "./client";
import type { HistorialEntry } from "../types";

export async function miHistorial(): Promise<HistorialEntry[]> {
  const { data } = await apiClient.get<HistorialEntry[]>("/api/historial/mio");
  return data;
}
