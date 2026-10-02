import type { AxiosAdapter, InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { apiClient } from "./client";

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
});
