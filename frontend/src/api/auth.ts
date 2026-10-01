import { apiClient } from "./client";
import type { Usuario } from "../types";

export interface AuthResponse {
  token: string;
  email: string;
  nombre: string;
  apellido: string;
  rol: Usuario["rol"];
}

export interface RegistroPacienteDatos {
  nombre: string;
  apellido: string;
  email: string;
  password: string;
  telefono?: string;
  dni: string;
  fechaNacimiento: string;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/api/auth/login", { email, password });
  return data;
}

export async function registrarPaciente(datos: RegistroPacienteDatos): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/api/auth/registro", datos);
  return data;
}

export function guardarSesion(auth: AuthResponse) {
  localStorage.setItem("token", auth.token);
  const usuario: Usuario = { email: auth.email, nombre: auth.nombre, apellido: auth.apellido, rol: auth.rol };
  localStorage.setItem("usuario", JSON.stringify(usuario));
}

export function obtenerUsuarioGuardado(): Usuario | null {
  const raw = localStorage.getItem("usuario");
  return raw ? (JSON.parse(raw) as Usuario) : null;
}

export function cerrarSesion() {
  localStorage.removeItem("token");
  localStorage.removeItem("usuario");
}
