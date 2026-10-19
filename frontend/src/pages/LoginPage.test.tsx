import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../api/auth";
import { apiClient } from "../api/client";
import { usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import { LoginPage } from "./LoginPage";

vi.mock("../api/auth", async (importOriginal) => {
  const real = await importOriginal<typeof import("../api/auth")>();
  return { ...real, login: vi.fn() };
});

const respuestaOk: authApi.AuthResponse = { token: "jwt", email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", rol: "PACIENTE" };

async function completar(email: string, password: string) {
  await userEvent.type(screen.getByLabelText("Email"), email);
  await userEvent.type(screen.getByLabelText("Contraseña"), password);
  await userEvent.click(screen.getByRole("button", { name: "Ingresar" }));
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.mocked(authApi.login).mockReset();
  });

  it("con credenciales válidas inicia sesión y va al inicio", async () => {
    vi.mocked(authApi.login).mockResolvedValue(respuestaOk);
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await completar("ana@clinica.test", "paciente123");

    await waitFor(() => expect(screen.getByTestId("ubicacion")).toHaveTextContent(/^\/$/));
    expect(authApi.login).toHaveBeenCalledWith("ana@clinica.test", "paciente123");
    expect(localStorage.getItem("token")).toBe("jwt");
  });

  it("si la API rechaza el login muestra el mensaje y se queda en la página", async () => {
    const respuesta = { status: 401, data: { error: "Email o contraseña incorrectos" } } as AxiosResponse;
    vi.mocked(authApi.login).mockRejectedValue(new AxiosError("fallo", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await completar("ana@clinica.test", "mala");

    expect(await screen.findByText("Email o contraseña incorrectos")).toBeInTheDocument();
    expect(screen.getByTestId("ubicacion")).toHaveTextContent("/login");
    expect(localStorage.getItem("token")).toBeNull();
  });

  it("si no hay conexión con el servidor lo dice, sin acusar a las credenciales", async () => {
    vi.mocked(authApi.login).mockRejectedValue(new AxiosError("Network Error", "ERR_NETWORK"));
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await completar("ana@clinica.test", "paciente123");

    expect(await screen.findByText(/No se pudo conectar con el servidor/)).toBeInTheDocument();
    expect(screen.queryByText(/Email o contraseña incorrectos/)).not.toBeInTheDocument();
  });

  it("ante una falla inesperada muestra un mensaje genérico, también sin acusar a las credenciales", async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error("boom"));
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await completar("ana@clinica.test", "paciente123");

    expect(await screen.findByText("Ocurrió un error. Probá de nuevo.")).toBeInTheDocument();
  });

  it("mientras ingresa deshabilita el botón para evitar doble envío", async () => {
    let terminar: (valor: authApi.AuthResponse) => void = () => {};
    vi.mocked(authApi.login).mockReturnValue(new Promise((resolve) => { terminar = resolve; }));
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await completar("ana@clinica.test", "paciente123");

    expect(screen.getByRole("button", { name: "Ingresando…" })).toBeDisabled();
    terminar(respuestaOk);
    await waitFor(() => expect(screen.getByTestId("ubicacion")).toHaveTextContent(/^\/$/));
  });

  it("muestra un aviso cuando la sesión se cerró sola porque el servidor dejó de aceptar el token", async () => {
    const adaptadorOriginal = apiClient.defaults.adapter;
    renderConSesion(<LoginPage />, { usuario: usuario("PACIENTE"), ruta: "/login", path: "/login" });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    apiClient.defaults.adapter = async (config) => {
      throw new AxiosError("x", "ERR_BAD_REQUEST", config, undefined, { data: {}, status: 401, statusText: "", headers: {}, config });
    };
    await act(async () => {
      await apiClient.get("/api/turnos/mios").catch(() => {});
    });
    apiClient.defaults.adapter = adaptadorOriginal;

    expect(await screen.findByRole("status")).toHaveTextContent("Tu sesión se cerró");
  });

  it("no deja enviar con los campos vacíos", async () => {
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await userEvent.click(screen.getByRole("button", { name: "Ingresar" }));

    expect(authApi.login).not.toHaveBeenCalled();
  });

  describe("cuentas de demostración", () => {
    afterEach(() => {
      vi.unstubAllEnvs();
    });

    it("en la versión normal no se ofrecen", () => {
      vi.stubEnv("VITE_MODO_DEMO", "false");
      renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

      expect(screen.queryByText(/cuenta de demostración/)).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Entrar como/ })).not.toBeInTheDocument();
    });

    it("en la versión de demostración hay un botón por rol", () => {
      vi.stubEnv("VITE_MODO_DEMO", "true");
      renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

      for (const rol of ["paciente", "médico", "recepción", "administrador"]) {
        expect(screen.getByRole("button", { name: `Entrar como ${rol}` })).toBeInTheDocument();
      }
    });

    it.each([
      ["paciente", "paciente@clinica-demo.test", "demo-clinica-2026"],
      ["médico", "medico@clinica-demo.test", "demo-clinica-2026"],
      ["recepción", "recepcion@clinica-demo.test", "demo-clinica-2026"],
      ["administrador", "admin@clinica.test", "admin1234"],
    ])("«Entrar como %s» inicia sesión con esa cuenta de un solo clic", async (rol, email, clave) => {
      vi.stubEnv("VITE_MODO_DEMO", "true");
      vi.mocked(authApi.login).mockResolvedValue(respuestaOk);
      renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

      await userEvent.click(screen.getByRole("button", { name: `Entrar como ${rol}` }));

      await waitFor(() => expect(authApi.login).toHaveBeenCalledWith(email, clave));
      await waitFor(() => expect(screen.getByTestId("ubicacion")).toHaveTextContent(/^\/$/));
    });

    it("si el servidor no responde lo dice y los botones vuelven a estar disponibles", async () => {
      vi.stubEnv("VITE_MODO_DEMO", "true");
      vi.mocked(authApi.login).mockRejectedValue(new AxiosError("Network Error", "ERR_NETWORK"));
      renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

      await userEvent.click(screen.getByRole("button", { name: "Entrar como paciente" }));

      expect(await screen.findByText(/No se pudo conectar con el servidor/)).toBeInTheDocument();
      expect(screen.queryByText(/Email o contraseña incorrectos/)).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Entrar como paciente" })).toBeEnabled();
    });
  });
});
