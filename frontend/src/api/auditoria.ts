import { apiClient } from "./client";
import type { AccesoHistorial } from "../types";

/** Solo ADMIN: los 200 accesos más recientes a historias clínicas de otras personas. */
export async function accesosHistorial(): Promise<AccesoHistorial[]> {
  const { data } = await apiClient.get<AccesoHistorial[]>("/api/auditoria/accesos-historial");
  return data;
}
