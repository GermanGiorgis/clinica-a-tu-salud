import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient, registrarManejadorDeSesionVencida, TIMEOUT_MS } from "./client";

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
});
