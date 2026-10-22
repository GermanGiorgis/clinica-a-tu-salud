import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient, registrarManejadorDeSesionVencida, suscribirseALentitud, TIMEOUT_DEMO_MS, TIMEOUT_MS, UMBRAL_LENTO_MS } from "./client";

describe("apiClient", () => {
  const adaptadorOriginal = apiClient.defaults.adapter;
  let ultimaConfig: InternalAxiosRequestConfig | undefined;

  beforeEach(() => {
    ultimaConfig = undefined;
    // Se corta antes de la red: solo interesa qué request sale después de los interceptores.
    const adaptador: AxiosAdapter = async (config) => {
      ultimaConfig = config;
      return { data: {}, status: 200, statusText: "OK", headers: {}, config };
    };
    apiClient.defaults.adapter = adaptador;
  });

  afterEach(() => {
    apiClient.defaults.adapter = adaptadorOriginal;
    registrarManejadorDeSesionVencida(null);
  });

  it("agrega el token guardado como Bearer en el header Authorization", async () => {
    localStorage.setItem("token", "jwt-123");

    await apiClient.get("/api/turnos/mios");

    expect(ultimaConfig?.headers.Authorization).toBe("Bearer jwt-123");
  });

  it("no manda Authorization si no hay sesión", async () => {
    await apiClient.get("/api/medicos");

    expect(ultimaConfig?.headers.Authorization).toBeUndefined();
  });

  it("corta los pedidos que tardan demasiado en vez de esperar para siempre", async () => {
    await apiClient.get("/api/medicos");

    expect(TIMEOUT_MS).toBe(15_000);
    expect(ultimaConfig?.timeout).toBe(TIMEOUT_MS);
  });

  describe("sesión vencida", () => {
    function responderCon(status: number) {
      const adaptador: AxiosAdapter = async (config) => {
        const respuesta = { data: { error: "x" }, status, statusText: "", headers: {}, config };
        throw new AxiosError("fallo", "ERR_BAD_REQUEST", config, undefined, respuesta);
      };
      apiClient.defaults.adapter = adaptador;
    }

    it("un 401 con un token guardado avisa que la sesión venció y deja pasar el error", async () => {
      const manejador = vi.fn();
      registrarManejadorDeSesionVencida(manejador);
      localStorage.setItem("token", "jwt-vencido");
      responderCon(401);

      await expect(apiClient.get("/api/turnos/mios")).rejects.toThrow();

      expect(manejador).toHaveBeenCalledTimes(1);
    });

    it("un 401 sin token guardado no avisa nada (no había sesión que cerrar)", async () => {
      const manejador = vi.fn();
      registrarManejadorDeSesionVencida(manejador);
      responderCon(401);

      await expect(apiClient.get("/api/turnos/mios")).rejects.toThrow();

      expect(manejador).not.toHaveBeenCalled();
    });

    it("un 401 de login o registro son credenciales incorrectas y no cierran la sesión", async () => {
      const manejador = vi.fn();
      registrarManejadorDeSesionVencida(manejador);
      localStorage.setItem("token", "jwt-123");
      responderCon(401);

      await expect(apiClient.post("/api/auth/login", {})).rejects.toThrow();

      expect(manejador).not.toHaveBeenCalled();
    });

    it.each([403, 404, 409, 500])("un %i no se confunde con una sesión vencida", async (status) => {
      const manejador = vi.fn();
      registrarManejadorDeSesionVencida(manejador);
      localStorage.setItem("token", "jwt-123");
      responderCon(status);

      await expect(apiClient.get("/api/turnos/mios")).rejects.toThrow();

      expect(manejador).not.toHaveBeenCalled();
    });
  });

  describe("pedidos lentos (modo demostración: el servidor gratuito se está despertando)", () => {
    afterEach(() => {
      vi.useRealTimers();
      vi.unstubAllEnvs();
    });

    /** Un servidor que contesta recién cuando se llama a `responder` (o falla, si se le pide). */
    function servidorManual(fallar = false) {
      let responder: () => void = () => {};
      const espera = new Promise<void>((r) => {
        responder = r;
      });
      const adaptador: AxiosAdapter = async (config) => {
        await espera;
        if (fallar) {
          throw new AxiosError("Network Error", "ERR_NETWORK", config);
        }
        return { data: {}, status: 200, statusText: "OK", headers: {}, config };
      };
      apiClient.defaults.adapter = adaptador;
      return responder;
    }

    it("avisa cuando un pedido pasa del umbral y de nuevo cuando termina", async () => {
      vi.useFakeTimers();
      vi.stubEnv("VITE_MODO_DEMO", "true");
      const oyente = vi.fn();
      const baja = suscribirseALentitud(oyente);
      const responder = servidorManual();

      const pedido = apiClient.get("/api/medicos");
      await vi.advanceTimersByTimeAsync(UMBRAL_LENTO_MS - 1);
      expect(oyente).not.toHaveBeenCalled();
      await vi.advanceTimersByTimeAsync(2);
      expect(oyente).toHaveBeenCalledTimes(1);
      expect(oyente).toHaveBeenLastCalledWith(true);

      responder();
      await pedido;
      expect(oyente).toHaveBeenCalledTimes(2);
      expect(oyente).toHaveBeenLastCalledWith(false);
      baja();
    });

    it("un pedido rápido no avisa nada, ni siquiera después de que pase el umbral", async () => {
      vi.useFakeTimers();
      vi.stubEnv("VITE_MODO_DEMO", "true");
      const oyente = vi.fn();
      const baja = suscribirseALentitud(oyente);
      const responder = servidorManual();

      const pedido = apiClient.get("/api/medicos");
      responder();
      await pedido;
      await vi.advanceTimersByTimeAsync(UMBRAL_LENTO_MS * 3);

      expect(oyente).not.toHaveBeenCalled();
      baja();
    });

    it("si el pedido lento falla, el aviso también se retira", async () => {
      vi.useFakeTimers();
      vi.stubEnv("VITE_MODO_DEMO", "true");
      const oyente = vi.fn();
      const baja = suscribirseALentitud(oyente);
      const responder = servidorManual(true);

      const pedido = apiClient.get("/api/medicos").catch(() => "fallo");
      await vi.advanceTimersByTimeAsync(UMBRAL_LENTO_MS + 1);
      expect(oyente).toHaveBeenLastCalledWith(true);

      responder();
      await pedido;
      expect(oyente).toHaveBeenLastCalledWith(false);
      baja();
    });

    it("con varios pedidos lentos, el aviso se retira recién cuando termina el último", async () => {
      vi.useFakeTimers();
      vi.stubEnv("VITE_MODO_DEMO", "true");
      const oyente = vi.fn();
      const baja = suscribirseALentitud(oyente);
      const liberadores: (() => void)[] = [];
      apiClient.defaults.adapter = async (config) => {
        await new Promise<void>((r) => liberadores.push(r));
        return { data: {}, status: 200, statusText: "OK", headers: {}, config };
      };

      const primero = apiClient.get("/api/medicos");
      const segundo = apiClient.get("/api/especialidades");
      await vi.advanceTimersByTimeAsync(UMBRAL_LENTO_MS + 1);
      expect(oyente).toHaveBeenLastCalledWith(true);

      liberadores[0]();
      await primero;
      expect(oyente).not.toHaveBeenLastCalledWith(false);

      liberadores[1]();
      await segundo;
      expect(oyente).toHaveBeenLastCalledWith(false);
      baja();
    });

    it("fuera del modo demostración no se hace ningún seguimiento", async () => {
      vi.useFakeTimers();
      vi.stubEnv("VITE_MODO_DEMO", "false");
      const oyente = vi.fn();
      const baja = suscribirseALentitud(oyente);
      const responder = servidorManual();

      const pedido = apiClient.get("/api/medicos");
      await vi.advanceTimersByTimeAsync(UMBRAL_LENTO_MS * 3);
      responder();
      await pedido;

      expect(oyente).not.toHaveBeenCalled();
      baja();
    });

    it("quien se da de baja deja de enterarse", async () => {
      vi.useFakeTimers();
      vi.stubEnv("VITE_MODO_DEMO", "true");
      const oyente = vi.fn();
      suscribirseALentitud(oyente)();
      const responder = servidorManual();

      const pedido = apiClient.get("/api/medicos");
      await vi.advanceTimersByTimeAsync(UMBRAL_LENTO_MS + 1);
      responder();
      await pedido;

      expect(oyente).not.toHaveBeenCalled();
    });

    it("en la demostración el pedido espera 90 segundos antes de cortarse (el arranque en frío tarda)", async () => {
      vi.stubEnv("VITE_MODO_DEMO", "true");
      vi.resetModules();

      const { apiClient: clienteDemo } = await import("./client");

      expect(TIMEOUT_DEMO_MS).toBe(90_000);
      expect(clienteDemo.defaults.timeout).toBe(TIMEOUT_DEMO_MS);
    });
  });
});
