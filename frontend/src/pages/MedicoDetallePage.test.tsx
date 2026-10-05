import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as afiliacionesApi from "../api/afiliaciones";
import * as medicosApi from "../api/medicos";
import * as turnosApi from "../api/turnos";
import { turno, usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import type { Afiliacion, Medico, Usuario } from "../types";
import { MedicoDetallePage } from "./MedicoDetallePage";

vi.mock("../api/medicos");
vi.mock("../api/turnos");
vi.mock("../api/afiliaciones");

const pedro: Medico = {
  id: 7,
  nombre: "Pedro",
  apellido: "Prueba",
  matricula: "MP-7",
  especialidades: [{ id: 2, nombre: "Dermatología", descripcion: null }],
};

const osde: Afiliacion = {
  id: 3,
  obraSocialId: 1,
  obraSocialNombre: "OSDE",
  numeroAfiliado: "123456",
  plan: null,
  vigenteDesde: "2026-01-01",
  vigenteHasta: null,
};

function montar(sesion: Usuario | null) {
  renderConSesion(<MedicoDetallePage />, { usuario: sesion, ruta: "/medicos/7", path: "/medicos/:id" });
}

/** Elige el horario de las 09:30 (la fecha por defecto es hoy, la que muestre el input). */
async function elegirHorario() {
  await userEvent.click(await screen.findByRole("button", { name: "09:30" }));
}

function fechaMostrada(): string {
  return (screen.getByLabelText(/Fecha/) as HTMLInputElement).value;
}

describe("MedicoDetallePage (reserva de turnos)", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(medicosApi.obtenerMedico).mockResolvedValue(pedro);
    vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: "2030-01-10", horariosLibres: ["09:00:00", "09:30:00"] });
    vi.mocked(afiliacionesApi.misAfiliaciones).mockResolvedValue([]);
    vi.mocked(turnosApi.crearTurno).mockResolvedValue(turno());
  });

  it("muestra los datos del médico y sus horarios libres", async () => {
    montar(null);

    expect(await screen.findByRole("heading", { name: "Pedro Prueba" })).toBeInTheDocument();
    expect(screen.getByText(/Matrícula MP-7/)).toBeInTheDocument();
    expect(screen.getByText(/Dermatología/)).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "09:00" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "09:30" })).toBeInTheDocument();
  });

  it("si el médico no existe muestra el error", async () => {
    vi.mocked(medicosApi.obtenerMedico).mockRejectedValue(new Error("404"));
    montar(null);

    expect(await screen.findByText("No se encontró el médico.")).toBeInTheDocument();
  });

  it("si ese día no hay horarios libres lo dice", async () => {
    vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: "2030-01-10", horariosLibres: [] });
    montar(null);

    expect(await screen.findByText("No hay horarios libres ese día.")).toBeInTheDocument();
  });

  it("cambiar la fecha vuelve a consultar la disponibilidad de ese día", async () => {
    montar(null);
    await screen.findByRole("button", { name: "09:00" });

    const campo = screen.getByLabelText(/Fecha/);
    await userEvent.clear(campo);
    await userEvent.type(campo, "2099-03-04");

    await waitFor(() => expect(medicosApi.obtenerDisponibilidad).toHaveBeenLastCalledWith(7, "2099-03-04"));
  });

  it("sin sesión, elegir un horario pide iniciar sesión o crear cuenta en vez de reservar", async () => {
    montar(null);

    await elegirHorario();

    expect(screen.getByRole("link", { name: "iniciar sesión" })).toHaveAttribute("href", "/login");
    expect(screen.getByRole("link", { name: "crear una cuenta" })).toHaveAttribute("href", "/registro");
    expect(screen.queryByRole("button", { name: /Reservar/ })).not.toBeInTheDocument();
  });

  describe("como paciente", () => {
    it("reserva para sí mismo con el horario elegido y el motivo, y confirma", async () => {
      montar(usuario("PACIENTE"));

      await elegirHorario();
      await userEvent.type(screen.getByLabelText(/Motivo de la consulta/), "Control anual");
      await userEvent.click(screen.getByRole("button", { name: /^Reservar/ }));

      await waitFor(() =>
        expect(turnosApi.crearTurno).toHaveBeenCalledWith({
          medicoId: 7,
          inicio: `${fechaMostrada()}T09:30:00`,
          motivoConsulta: "Control anual",
          pacienteDni: undefined,
          afiliacionId: undefined,
        }),
      );
      expect(await screen.findByText(/¡Turno reservado!/)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Ver mis turnos" })).toBeInTheDocument();
    });

    it("no pide el DNI de nadie: un paciente reserva siempre para sí mismo", async () => {
      montar(usuario("PACIENTE"));

      await elegirHorario();

      expect(screen.queryByLabelText(/DNI del paciente/)).not.toBeInTheDocument();
    });

    it("después de reservar vuelve a pedir la disponibilidad (el horario tomado ya no debe aparecer)", async () => {
      montar(usuario("PACIENTE"));
      await elegirHorario();
      const llamadasAntes = vi.mocked(medicosApi.obtenerDisponibilidad).mock.calls.length;

      await userEvent.click(screen.getByRole("button", { name: /^Reservar/ }));

      await screen.findByText(/¡Turno reservado!/);
      expect(vi.mocked(medicosApi.obtenerDisponibilidad).mock.calls.length).toBeGreaterThan(llamadasAntes);
    });

    it("si el horario ya lo ocupó otra persona, vuelve a pedir la disponibilidad para que deje de verse", async () => {
    const respuesta = { status: 409, data: { error: "Ese horario ya fue reservado, elegí otro" } } as AxiosResponse;
    vi.mocked(turnosApi.crearTurno).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    montar(usuario("PACIENTE"));
    await elegirHorario();
    // Cuando el paciente reintenta, el servidor ya no ofrece las 09:30.
    vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: "2030-01-10", horariosLibres: ["09:00:00"] });

    await userEvent.click(screen.getByRole("button", { name: /^Reservar/ }));

    expect(await screen.findByText("Ese horario ya fue reservado, elegí otro")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("button", { name: "09:30" })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "09:00" })).toBeInTheDocument();
    // y ya no queda elegido un horario que no existe más
    expect(screen.queryByRole("button", { name: /^Reservar/ })).not.toBeInTheDocument();
  });

  it("no deja elegir una fecha más allá de la ventana de reserva de 60 días", async () => {
    montar(null);
    await screen.findByRole("button", { name: "09:00" });

    const campo = screen.getByLabelText(/Fecha/) as HTMLInputElement;
    const limite = new Date();
    limite.setDate(limite.getDate() + 60);

    expect(campo.max).toBe(limite.toLocaleDateString("sv-SE"));
    expect(campo.min).toBe(new Date().toLocaleDateString("sv-SE"));
  });

  it("si tiene afiliaciones puede elegir con cuál se atiende, y se manda su id", async () => {
      vi.mocked(afiliacionesApi.misAfiliaciones).mockResolvedValue([osde]);
      montar(usuario("PACIENTE"));

      await elegirHorario();
      await userEvent.selectOptions(await screen.findByLabelText(/Cobertura/), "OSDE — N° 123456");
      await userEvent.click(screen.getByRole("button", { name: /^Reservar/ }));

      await waitFor(() => expect(turnosApi.crearTurno).toHaveBeenCalledWith(expect.objectContaining({ afiliacionId: 3 })));
    });

    it("sin afiliaciones no ofrece el selector de cobertura", async () => {
      montar(usuario("PACIENTE"));

      await elegirHorario();

      expect(screen.queryByLabelText(/Cobertura/)).not.toBeInTheDocument();
    });

    it("si el horario ya lo ocupó otra persona muestra el mensaje de la API y no confirma", async () => {
      const respuesta = { status: 409, data: { error: "Ese horario ya no está disponible" } } as AxiosResponse;
      vi.mocked(turnosApi.crearTurno).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
      montar(usuario("PACIENTE"));

      await elegirHorario();
      await userEvent.click(screen.getByRole("button", { name: /^Reservar/ }));

      expect(await screen.findByText("Ese horario ya no está disponible")).toBeInTheDocument();
      expect(screen.queryByText(/¡Turno reservado!/)).not.toBeInTheDocument();
    });
  });

  describe("como médico o admin (reservando a nombre de un paciente)", () => {
    it.each(["MEDICO", "ADMIN"] as const)("%s: pide el DNI y no deja reservar hasta completarlo", async (rol) => {
      montar(usuario(rol));

      await elegirHorario();

      expect(screen.getByRole("button", { name: /^Reservar/ })).toBeDisabled();
      await userEvent.type(screen.getByLabelText(/DNI del paciente/), "99887766");
      expect(screen.getByRole("button", { name: /^Reservar/ })).toBeEnabled();
    });

    it("manda el DNI del paciente y no ofrece cobertura ni 'Ver mis turnos'", async () => {
      montar(usuario("ADMIN"));

      await elegirHorario();
      await userEvent.type(screen.getByLabelText(/DNI del paciente/), "99887766");
      await userEvent.click(screen.getByRole("button", { name: /^Reservar/ }));

      await waitFor(() =>
        expect(turnosApi.crearTurno).toHaveBeenCalledWith(expect.objectContaining({ medicoId: 7, pacienteDni: "99887766" })),
      );
      expect(await screen.findByText("¡Turno reservado para ese paciente!")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Ver mis turnos" })).not.toBeInTheDocument();
      expect(afiliacionesApi.misAfiliaciones).not.toHaveBeenCalled();
    });
  });
});
