import axios, { isAxiosError } from "axios";

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

/** Si el servidor no contesta en este tiempo se corta el pedido: sin esto, una conexión colgada deja el botón en "Reservando…" para siempre. */
export const TIMEOUT_MS = 15_000;

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: TIMEOUT_MS,
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let alVencerLaSesion: (() => void) | null = null;

/** El AuthProvider se registra acá para enterarse cuando el servidor deja de aceptar el token. */
export function registrarManejadorDeSesionVencida(manejador: (() => void) | null) {
  alVencerLaSesion = manejador;
}

apiClient.interceptors.response.use(
  (respuesta) => respuesta,
  (error: unknown) => {
    // Un 401 con un token guardado = el token venció o la cuenta se desactivó. Los de /api/auth/ son
    // simplemente credenciales incorrectas y no cierran ninguna sesión.
    if (
      isAxiosError(error) &&
      error.response?.status === 401 &&
      localStorage.getItem("token") &&
      !error.config?.url?.startsWith("/api/auth/")
    ) {
      alVencerLaSesion?.();
    }
    return Promise.reject(error);
  },
);
