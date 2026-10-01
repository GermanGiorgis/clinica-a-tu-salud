import { apiClient } from "./client";
import type { OrdenEstudio } from "../types";

export interface CrearOrdenEstudioDatos {
  estudioId: number;
  indicaciones?: string;
}

export async function crearOrdenEstudio(consultaId: number, datos: CrearOrdenEstudioDatos): Promise<OrdenEstudio> {
  const { data } = await apiClient.post<OrdenEstudio>(`/api/consultas/${consultaId}/ordenes-estudio`, datos);
  return data;
}

export async function cargarResultado(ordenId: number, resultado: string): Promise<OrdenEstudio> {
  const { data } = await apiClient.patch<OrdenEstudio>(`/api/ordenes-estudio/${ordenId}/resultado`, { resultado });
  return data;
}
