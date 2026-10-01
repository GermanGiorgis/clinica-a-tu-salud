import { isAxiosError } from "axios";

interface CuerpoError {
  error?: string;
  errores?: Record<string, string>;
}

/** Los controllers devuelven {"error": "..."} o, en validaciones, {"error": "...", "errores": {...}}. */
export function extraerError(err: unknown, fallback = "Ocurrió un error. Probá de nuevo."): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as CuerpoError | undefined;
    if (data?.error && data.errores) {
      const detalles = Object.entries(data.errores)
        .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
        .join(", ");
      return `${data.error} (${detalles})`;
    }
    if (data?.error) {
      return data.error;
    }
  }
  return fallback;
}
