import { apiClient } from "./client";
import type { Rol, UsuarioListado } from "../types";

/** Solo ADMIN. Desactivar corta el acceso de inmediato, aunque el token todavía no haya vencido. */
export async function cambiarActivo(usuarioId: number, activo: boolean): Promise<void> {
  await apiClient.patch(`/api/usuarios/${usuarioId}/activo`, { activo });
}

/** Solo ADMIN: todas las cuentas, activas o no. */
export async function listarUsuarios(): Promise<UsuarioListado[]> {
  const { data } = await apiClient.get<UsuarioListado[]>("/api/usuarios");
  return data;
}

export interface NuevoUsuario {
  rol: Rol;
  nombre: string;
  apellido: string;
  email: string;
  password: string;
  telefono?: string;
  /** Solo PACIENTE. */
  dni?: string;
  fechaNacimiento?: string;
  /** Solo MEDICO. */
  matricula?: string;
  especialidadIds?: number[];
}

/** Solo ADMIN: crea cualquier tipo de cuenta (los pacientes que da de alta el personal nacen con la identidad verificada). */
export async function crearUsuario(usuario: NuevoUsuario): Promise<UsuarioListado> {
  const { data } = await apiClient.post<UsuarioListado>("/api/usuarios", usuario);
  return data;
}
