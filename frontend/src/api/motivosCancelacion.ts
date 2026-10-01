import { apiClient } from "./client";
import type { MotivoCancelacion } from "../types";

export async function listarMotivosCancelacion(): Promise<MotivoCancelacion[]> {
  const { data } = await apiClient.get<MotivoCancelacion[]>("/api/motivos-cancelacion");
  return data;
}
