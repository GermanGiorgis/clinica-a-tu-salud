import { apiClient } from "./client";
import type { Receta } from "../types";

export interface CrearRecetaDatos {
  observaciones?: string;
  items: {
    medicamentoId: number;
    dosis: string;
    frecuencia: string;
    duracionDias: number;
  }[];
}

export async function crearReceta(consultaId: number, datos: CrearRecetaDatos): Promise<Receta> {
  const { data } = await apiClient.post<Receta>(`/api/consultas/${consultaId}/recetas`, datos);
  return data;
}
