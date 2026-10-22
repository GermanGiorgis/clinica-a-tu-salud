import axios, { isAxiosError } from "axios";
import { modoDemo } from "../config/demo";

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

/** Si el servidor no contesta en este tiempo se corta el pedido: sin esto, una conexión colgada deja el botón en "Reservando…" para siempre. */
export const TIMEOUT_MS = 15_000;

/**
 * La demo en línea corre en un hosting que apaga el servidor tras unos minutos sin visitas y lo vuelve a arrancar con el
 * primer pedido (la JVM tarda unos 30-40 segundos): ahí se espera bastante más antes de darse por vencido.
 */
export const TIMEOUT_DEMO_MS = 90_000;

/** Un pedido que tarda más que esto se considera lento (en la demo: el servidor se está despertando). */
export const UMBRAL_LENTO_MS = 4_000;

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: modoDemo() ? TIMEOUT_DEMO_MS : TIMEOUT_MS,
});

interface SeguimientoDePedido {
  temporizador: ReturnType<typeof setTimeout>;
  lento: boolean;
}

const seguimientos = new WeakMap<object, SeguimientoDePedido>();
const oyentesDeLentitud = new Set<(hayPedidosLentos: boolean) => void>();
let pedidosLentos = 0;

function avisarLentitud() {
  for (const oyente of oyentesDeLentitud) oyente(pedidosLentos > 0);
}

/** Para mostrar "el servidor se está despertando": se avisa cuando hay algún pedido lento y de nuevo cuando ya no. */
export function suscribirseALentitud(oyente: (hayPedidosLentos: boolean) => void): () => void {
  oyentesDeLentitud.add(oyente);
  return () => {
    oyentesDeLentitud.delete(oyente);
  };
}

function terminarSeguimiento(config: object | undefined) {
  const seguimiento = config && seguimientos.get(config);
  if (!config || !seguimiento) return;
  clearTimeout(seguimiento.temporizador);
  seguimientos.delete(config);
  if (seguimiento.lento) {
    pedidosLentos -= 1;
    avisarLentitud();
  }
}

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (modoDemo()) {
    const seguimiento: SeguimientoDePedido = {
      lento: false,
      temporizador: setTimeout(() => {
        seguimiento.lento = true;
        pedidosLentos += 1;
        avisarLentitud();
      }, UMBRAL_LENTO_MS),
    };
    seguimientos.set(config, seguimiento);
  }
  return config;
});

let alVencerLaSesion: (() => void) | null = null;

/** El AuthProvider se registra acá para enterarse cuando el servidor deja de aceptar el token. */
export function registrarManejadorDeSesionVencida(manejador: (() => void) | null) {
  alVencerLaSesion = manejador;
}

apiClient.interceptors.response.use(
  (respuesta) => {
    terminarSeguimiento(respuesta.config);
    return respuesta;
  },
  (error: unknown) => {
    if (isAxiosError(error)) terminarSeguimiento(error.config);
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
