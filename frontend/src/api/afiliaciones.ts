import { apiClient } from "./client";
import type { Afiliacion } from "../types";

export interface CrearAfiliacionDatos {
  obraSocialId: number;
  numeroAfiliado: string;
  plan?: string;
  vigenteDesde: string;
  vigenteHasta?: string;
}

export async function misAfiliaciones(): Promise<Afiliacion[]> {
  const { data } = await apiClient.get<Afiliacion[]>("/api/afiliaciones/mias");
  return data;
}

export async function crearAfiliacion(datos: CrearAfiliacionDatos): Promise<Afiliacion> {
  const { data } = await apiClient.post<Afiliacion>("/api/afiliaciones", datos);
  return data;
}

export async function eliminarAfiliacion(id: number): Promise<void> {
  await apiClient.delete(`/api/afiliaciones/${id}`);
}
