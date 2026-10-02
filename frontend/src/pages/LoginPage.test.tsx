import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../api/auth";
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

  it("si no hay respuesta del servidor muestra el mensaje genérico de credenciales", async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error("Network Error"));
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await completar("ana@clinica.test", "paciente123");

    expect(await screen.findByText("Email o contraseña incorrectos")).toBeInTheDocument();
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

  it("no deja enviar con los campos vacíos", async () => {
    renderConSesion(<LoginPage />, { ruta: "/login", path: "/login" });

    await userEvent.click(screen.getByRole("button", { name: "Ingresar" }));

    expect(authApi.login).not.toHaveBeenCalled();
  });
});
