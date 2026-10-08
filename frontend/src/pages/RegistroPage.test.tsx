import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "../api/auth";
import { renderConSesion } from "../test/render";
import { RegistroPage } from "./RegistroPage";

vi.mock("../api/auth", async (importOriginal) => {
  const real = await importOriginal<typeof import("../api/auth")>();
  return { ...real, registrarPaciente: vi.fn() };
});

const respuestaOk: authApi.AuthResponse = { token: "jwt", email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", rol: "PACIENTE" };

function montar() {
  renderConSesion(<RegistroPage />, { ruta: "/registro", path: "/registro" });
}

async function completar(password = "una-clave-segura-1") {
  await userEvent.type(screen.getByLabelText("Nombre"), "Ana");
  await userEvent.type(screen.getByLabelText("Apellido"), "Paz");
  await userEvent.type(screen.getByLabelText("Email"), "ana@clinica.test");
  await userEvent.type(screen.getByLabelText(/^Contraseña/), password);
  await userEvent.type(screen.getByLabelText(/^DNI/), "30111222");
  await userEvent.type(screen.getByLabelText("Fecha de nacimiento"), "1990-05-20");
  await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));
}

describe("RegistroPage", () => {
  beforeEach(() => {
    vi.mocked(authApi.registrarPaciente).mockReset();
  });

  it("pide una contraseña de entre 10 y 72 caracteres, igual que el servidor", () => {
    montar();

    const campo = screen.getByLabelText(/^Contraseña/);
    expect(campo).toHaveAttribute("minLength", "10");
    expect(campo).toHaveAttribute("maxLength", "72");
    expect(screen.getByText(/entre 10 y 72 caracteres/)).toBeInTheDocument();
  });

  it("el DNI acepta solo números: si lo pegan con puntos o letras queda limpio", async () => {
    montar();
    const dni = screen.getByLabelText(/^DNI/);

    await userEvent.type(dni, "30.111.222");

    expect(dni).toHaveValue("30111222");
    expect(dni).toHaveAttribute("inputMode", "numeric");
    await userEvent.clear(dni);
    await userEvent.type(dni, "ab12cd");
    expect(dni).toHaveValue("12");
  });

  it("avisa que la clínica tiene que verificar la identidad llevando el DNI", () => {
    montar();

    expect(screen.getByText(/verificar tu identidad/)).toBeInTheDocument();
  });

  it("manda los datos cargados, sin teléfono si se deja vacío, y va al inicio", async () => {
    vi.mocked(authApi.registrarPaciente).mockResolvedValue(respuestaOk);
    montar();

    await completar();

    await waitFor(() => expect(screen.getByTestId("ubicacion")).toHaveTextContent(/^\/$/));
    expect(authApi.registrarPaciente).toHaveBeenCalledWith({
      nombre: "Ana", apellido: "Paz", email: "ana@clinica.test", password: "una-clave-segura-1",
      telefono: undefined, dni: "30111222", fechaNacimiento: "1990-05-20",
    });
  });

  it("muestra el mensaje del servidor si el alta falla (por ejemplo, por el límite de altas por IP)", async () => {
    const respuesta = { status: 429, data: { error: "Demasiados intentos. Probá de nuevo en 40 minuto(s)" } } as AxiosResponse;
    vi.mocked(authApi.registrarPaciente).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    montar();

    await completar();

    expect(await screen.findByText("Demasiados intentos. Probá de nuevo en 40 minuto(s)")).toBeInTheDocument();
    expect(screen.getByTestId("ubicacion")).toHaveTextContent("/registro");
  });

  it("muestra el detalle por campo cuando el servidor rechaza los datos", async () => {
    const respuesta = {
      status: 400,
      data: { error: "Revisá los datos ingresados", errores: { password: "La contraseña debe tener al menos 10 caracteres" } },
    } as AxiosResponse;
    vi.mocked(authApi.registrarPaciente).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    montar();

    await completar();

    expect(await screen.findByText(/Contraseña: La contraseña debe tener al menos 10 caracteres/)).toBeInTheDocument();
  });

  it("si el email ya existe lo dice y no deja a medias la sesión", async () => {
    const respuesta = { status: 409, data: { error: "Ya existe una cuenta con ese email" } } as AxiosResponse;
    vi.mocked(authApi.registrarPaciente).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    montar();

    await completar();

    expect(await screen.findByText("Ya existe una cuenta con ese email")).toBeInTheDocument();
    expect(localStorage.getItem("token")).toBeNull();
  });
});
