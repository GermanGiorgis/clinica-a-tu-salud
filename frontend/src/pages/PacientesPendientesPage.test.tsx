import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as pacientesApi from "../api/pacientes";
import * as usuariosApi from "../api/usuarios";
import { usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import type { PacienteAVerificar } from "../types";
import { PacientesPendientesPage } from "./PacientesPendientesPage";

vi.mock("../api/pacientes");
vi.mock("../api/usuarios");

function paciente(extra: Partial<PacienteAVerificar> = {}): PacienteAVerificar {
  return {
    id: 1, usuarioId: 101, nombre: "Ana", apellido: "Paz", email: "ana@clinica.test", telefono: "11-5555-0000",
    dni: "30111222", fechaNacimiento: "1990-05-20", identidadVerificada: false, cuentaActiva: true, ...extra,
  };
}

function errorApi(status: number, mensaje: string) {
  return new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, { status, data: { error: mensaje } } as AxiosResponse);
}

function montar() {
  renderConSesion(<PacientesPendientesPage />, { usuario: usuario("ADMIN"), ruta: "/pacientes/pendientes", path: "/pacientes/pendientes" });
}

describe("PacientesPendientesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sin pendientes lo dice", async () => {
    montar();

    expect(await screen.findByText("No hay pacientes pendientes de verificación.")).toBeInTheDocument();
  });

  it("si falla la carga muestra el mensaje del servidor", async () => {
    vi.mocked(pacientesApi.pacientesPendientes).mockRejectedValue(errorApi(403, "No tenés permiso para hacer esto"));
    montar();

    expect(await screen.findByText("No tenés permiso para hacer esto")).toBeInTheDocument();
  });

  it("muestra los datos que hay que comparar con el documento", async () => {
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([paciente()]);
    montar();

    expect(await screen.findByText("Ana Paz")).toBeInTheDocument();
    expect(screen.getByText(/DNI 30111222/)).toBeInTheDocument();
    expect(screen.getByText("20/5/1990", { exact: false })).toBeInTheDocument();
    expect(screen.getByText("ana@clinica.test")).toBeInTheDocument();
    expect(screen.getByText(/11-5555-0000/)).toBeInTheDocument();
  });

  it("verificar llama a la API con el paciente y recarga la lista", async () => {
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([paciente({ id: 7 })]);
    vi.mocked(pacientesApi.verificarPaciente).mockResolvedValue(paciente({ id: 7, identidadVerificada: true }));
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Verificar identidad/ }));

    await waitFor(() => expect(pacientesApi.verificarPaciente).toHaveBeenCalledWith(7));
    await waitFor(() => expect(pacientesApi.pacientesPendientes).toHaveBeenCalledTimes(2));
  });

  it("si otro paciente ya tiene verificado ese DNI muestra el conflicto y no recarga", async () => {
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([paciente()]);
    vi.mocked(pacientesApi.verificarPaciente).mockRejectedValue(errorApi(409, "Ya hay otro paciente con la identidad verificada para ese DNI"));
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Verificar identidad/ }));

    expect(await screen.findByText(/Ya hay otro paciente con la identidad verificada/)).toBeInTheDocument();
    expect(pacientesApi.pacientesPendientes).toHaveBeenCalledTimes(1);
  });

  it("desactivar pide confirmación y, si se acepta, desactiva la cuenta del usuario (no el id del paciente)", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([paciente({ id: 7, usuarioId: 777 })]);
    vi.mocked(usuariosApi.cambiarActivo).mockResolvedValue();
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Desactivar cuenta/ }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("Ana Paz"));
    await waitFor(() => expect(usuariosApi.cambiarActivo).toHaveBeenCalledWith(777, false));
    await waitFor(() => expect(pacientesApi.pacientesPendientes).toHaveBeenCalledTimes(2));
  });

  it("si se cancela la confirmación no desactiva nada", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([paciente()]);
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Desactivar cuenta/ }));

    expect(usuariosApi.cambiarActivo).not.toHaveBeenCalled();
  });

  it("cada paciente tiene sus propios botones y actuar sobre uno no usa los datos del otro", async () => {
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([paciente({ id: 1, nombre: "Ana" }), paciente({ id: 2, nombre: "Beto", dni: "40222333" })]);
    vi.mocked(pacientesApi.verificarPaciente).mockResolvedValue(paciente());
    montar();

    const tarjetaDeBeto = (await screen.findByText("Beto Paz")).closest("li") as HTMLElement;
    await userEvent.click(within(tarjetaDeBeto).getByRole("button", { name: /Verificar identidad/ }));

    await waitFor(() => expect(pacientesApi.verificarPaciente).toHaveBeenCalledWith(2));
  });
});
