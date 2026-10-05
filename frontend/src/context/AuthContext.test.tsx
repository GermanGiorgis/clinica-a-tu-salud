import { act, render, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { AxiosError, type AxiosAdapter } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../api/auth";
import { apiClient } from "../api/client";
import { usuario } from "../test/fixtures";
import { AuthProvider, useAuth } from "./AuthContext";

vi.mock("../api/auth", async (importOriginal) => {
  // Se mantiene la lógica real de localStorage y solo se reemplazan las llamadas HTTP.
  const real = await importOriginal<typeof import("../api/auth")>();
  return { ...real, login: vi.fn(), registrarPaciente: vi.fn() };
});

const respuesta: authApi.AuthResponse = { token: "jwt-123", email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", rol: "PACIENTE" };
const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

describe("AuthProvider / useAuth", () => {
  beforeEach(() => {
    vi.mocked(authApi.login).mockReset();
    vi.mocked(authApi.registrarPaciente).mockReset();
  });

  it("arranca sin sesión", () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.usuario).toBeNull();
    expect(result.current.estaAutenticado).toBe(false);
  });

  it("recupera la sesión guardada al montar (sobrevive a recargar la página)", () => {
    localStorage.setItem("usuario", JSON.stringify(usuario("MEDICO")));

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.estaAutenticado).toBe(true);
    expect(result.current.usuario?.rol).toBe("MEDICO");
  });

  it("login guarda token y usuario, y deja la sesión iniciada", async () => {
    vi.mocked(authApi.login).mockResolvedValue(respuesta);
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(() => result.current.login("ana@clinica.test", "paciente123"));

    expect(authApi.login).toHaveBeenCalledWith("ana@clinica.test", "paciente123");
    expect(result.current.usuario).toEqual({ email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", rol: "PACIENTE" });
    expect(localStorage.getItem("token")).toBe("jwt-123");
  });

  it("si el login falla propaga el error y no deja sesión a medias", async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error("credenciales"));
    const { result } = renderHook(() => useAuth(), { wrapper });

    await expect(act(() => result.current.login("x@x.com", "mala"))).rejects.toThrow("credenciales");

    expect(result.current.estaAutenticado).toBe(false);
    expect(localStorage.getItem("token")).toBeNull();
  });

  it("registrarPaciente deja la sesión iniciada con el usuario nuevo", async () => {
    vi.mocked(authApi.registrarPaciente).mockResolvedValue(respuesta);
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(() =>
      result.current.registrarPaciente({
        nombre: "Ana", apellido: "Paz", email: "ana@clinica.test", password: "paciente123", dni: "123", fechaNacimiento: "1990-01-01",
      }),
    );

    expect(result.current.usuario?.email).toBe("ana@clinica.test");
    expect(localStorage.getItem("token")).toBe("jwt-123");
  });

  it("logout borra la sesión de memoria y de localStorage", () => {
    localStorage.setItem("usuario", JSON.stringify(usuario("PACIENTE")));
    localStorage.setItem("token", "jwt-123");
    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => result.current.logout());

    expect(result.current.estaAutenticado).toBe(false);
    expect(localStorage.getItem("token")).toBeNull();
    expect(localStorage.getItem("usuario")).toBeNull();
  });

  describe("cuando el servidor deja de aceptar el token", () => {
    const adaptadorOriginal = apiClient.defaults.adapter;

    function servidorRespondeCon(status: number) {
      const adaptador: AxiosAdapter = async (config) => {
        throw new AxiosError("fallo", "ERR_BAD_REQUEST", config, undefined, { data: {}, status, statusText: "", headers: {}, config });
      };
      apiClient.defaults.adapter = adaptador;
    }

    afterEach(() => {
      apiClient.defaults.adapter = adaptadorOriginal;
    });

    it("un 401 cierra la sesión sola, borra lo guardado y marca que fue por vencimiento", async () => {
      localStorage.setItem("usuario", JSON.stringify(usuario("PACIENTE")));
      localStorage.setItem("token", "jwt-vencido");
      const { result } = renderHook(() => useAuth(), { wrapper });
      servidorRespondeCon(401);

      await act(async () => {
        await apiClient.get("/api/turnos/mios").catch(() => {});
      });

      expect(result.current.estaAutenticado).toBe(false);
      expect(result.current.sesionVencida).toBe(true);
      expect(localStorage.getItem("token")).toBeNull();
      expect(localStorage.getItem("usuario")).toBeNull();
    });

    it("un error que no es 401 no toca la sesión", async () => {
      localStorage.setItem("usuario", JSON.stringify(usuario("PACIENTE")));
      localStorage.setItem("token", "jwt-123");
      const { result } = renderHook(() => useAuth(), { wrapper });
      servidorRespondeCon(500);

      await act(async () => {
        await apiClient.get("/api/turnos/mios").catch(() => {});
      });

      expect(result.current.estaAutenticado).toBe(true);
      expect(result.current.sesionVencida).toBe(false);
    });

    it("volver a iniciar sesión limpia el aviso de sesión vencida", async () => {
      localStorage.setItem("usuario", JSON.stringify(usuario("PACIENTE")));
      localStorage.setItem("token", "jwt-vencido");
      vi.mocked(authApi.login).mockResolvedValue(respuesta);
      const { result } = renderHook(() => useAuth(), { wrapper });
      servidorRespondeCon(401);
      await act(async () => {
        await apiClient.get("/api/turnos/mios").catch(() => {});
      });
      expect(result.current.sesionVencida).toBe(true);

      await act(() => result.current.login("ana@clinica.test", "paciente123"));

      expect(result.current.sesionVencida).toBe(false);
      expect(result.current.estaAutenticado).toBe(true);
    });

    it("al salir del AuthProvider deja de escuchar (no queda un manejador huérfano)", async () => {
      localStorage.setItem("token", "jwt-123");
      const { unmount } = renderHook(() => useAuth(), { wrapper });
      unmount();
      servidorRespondeCon(401);

      await expect(apiClient.get("/api/turnos/mios")).rejects.toThrow();

      expect(localStorage.getItem("token")).toBe("jwt-123");
    });
  });

  it("useAuth fuera de un AuthProvider avisa con un error claro", () => {
    const silenciar = vi.spyOn(console, "error").mockImplementation(() => {});
    function Sola() {
      useAuth();
      return null;
    }

    expect(() => render(<Sola />)).toThrow("useAuth debe usarse dentro de un AuthProvider");
    silenciar.mockRestore();
  });
});
