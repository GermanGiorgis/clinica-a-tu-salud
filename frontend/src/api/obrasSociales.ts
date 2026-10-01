import { apiClient } from "./client";
import type { ObraSocial } from "../types";

export async function listarObrasSociales(): Promise<ObraSocial[]> {
  const { data } = await apiClient.get<ObraSocial[]>("/api/obras-sociales");
  return data;
}
