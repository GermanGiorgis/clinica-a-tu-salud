import { apiClient } from "./client";

/** Solo ADMIN. Desactivar corta el acceso de inmediato, aunque el token todavía no haya vencido. */
export async function cambiarActivo(usuarioId: number, activo: boolean): Promise<void> {
  await apiClient.patch(`/api/usuarios/${usuarioId}/activo`, { activo });
}
