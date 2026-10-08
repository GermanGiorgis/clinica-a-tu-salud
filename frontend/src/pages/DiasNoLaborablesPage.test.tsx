import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as diasApi from "../api/diasNoLaborables";
import * as medicosApi from "../api/medicos";
import * as motivosApi from "../api/motivosCancelacion";
import { usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import type { DiaNoLaborable, TurnoAfectado } from "../types";
import { DiasNoLaborablesPage } from "./DiasNoLaborablesPage";

vi.mock("../api/diasNoLaborables");
vi.mock("../api/medicos");
vi.mock("../api/motivosCancelacion");

const vendimia: DiaNoLaborable = { id: 1, desde: "2030-03-10", hasta: "2030-03-10", descripcion: "Día de la Vendimia", medicoId: null, medicoNombre: null };
const licencia: DiaNoLaborable = { id: 2, desde: "2030-03-12", hasta: "2030-03-15", descripcion: "Licencia", medicoId: 7, medicoNombre: "Pedro Prueba" };

function afectado(extra: Partial<TurnoAfectado> = {}): TurnoAfectado {
  return {
    id: 50, inicio: "2030-03-10T09:00:00", estado: "RESERVADO", medicoId: 7, medicoNombre: "Pedro Prueba",
    pacienteNombre: "Pablo Paciente", pacienteTelefono: "261-555-0101", consultorioNombre: "Consultorio B", ...extra,
  };
}

function errorApi(status: number, mensaje: string) {
  return new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, { status, data: { error: mensaje } } as AxiosResponse);
}

function montar() {
  renderConSesion(<DiasNoLaborablesPage />, { usuario: usuario("RECEPCIONISTA"), ruta: "/dias-no-laborables", path: "/dias-no-laborables" });
}

async function completarFormulario(desde: string, descripcion: string, hasta?: string) {
  fireEventDate(screen.getByLabelText(/^Desde/), desde);
  if (hasta) fireEventDate(screen.getByLabelText(/^Hasta/), hasta);
  await userEvent.type(screen.getByLabelText(/Descripción/), descripcion);
}

function fireEventDate(campo: HTMLElement, valor: string) {
  // user-event no escribe bien en inputs de fecha: se asigna el valor como lo haría el navegador.
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
  setter.call(campo, valor);
  campo.dispatchEvent(new Event("input", { bubbles: true }));
}

describe("DiasNoLaborablesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(diasApi.listarDiasNoLaborables).mockResolvedValue([]);
    vi.mocked(diasApi.turnosAfectados).mockResolvedValue([]);
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([
      { id: 7, nombre: "Pedro", apellido: "Prueba", matricula: "MP-7", especialidades: [] },
    ]);
    vi.mocked(motivosApi.listarMotivosCancelacion).mockResolvedValue([{ id: 10, nombre: "Médico no disponible", descripcion: null }]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sin períodos cargados lo dice", async () => {
    montar();

    expect(await screen.findByText("No hay feriados ni ausencias cargados.")).toBeInTheDocument();
  });

  it("lista los períodos con su alcance: toda la clínica o un médico", async () => {
    vi.mocked(diasApi.listarDiasNoLaborables).mockResolvedValue([vendimia, licencia]);
    montar();

    const feriado = (await screen.findByText("Día de la Vendimia")).closest("li")!;
    expect(within(feriado).getByText("Toda la clínica")).toBeInTheDocument();
    const ausencia = screen.getByText("Licencia").closest("li")!;
    expect(within(ausencia).getByText("Solo Pedro Prueba")).toBeInTheDocument();
  });

  it("si falla la carga muestra el mensaje del servidor", async () => {
    vi.mocked(diasApi.listarDiasNoLaborables).mockRejectedValue(errorApi(403, "No tenés permiso para hacer esto"));
    montar();

    expect(await screen.findByText("No tenés permiso para hacer esto")).toBeInTheDocument();
  });

  it("no se puede cargar sin fecha ni descripción", async () => {
    montar();
    await screen.findByText("No hay feriados ni ausencias cargados.");

    expect(screen.getByRole("button", { name: /Cargar período/ })).toBeDisabled();
  });

  it("carga un feriado de un solo día para toda la clínica (hasta vacío = el mismo día)", async () => {
    vi.mocked(diasApi.crearDiaNoLaborable).mockResolvedValue(vendimia);
    montar();
    await screen.findByText("No hay feriados ni ausencias cargados.");

    await completarFormulario("2030-03-10", "Día de la Vendimia");
    await userEvent.click(screen.getByRole("button", { name: /Cargar período/ }));

    await waitFor(() =>
      expect(diasApi.crearDiaNoLaborable).toHaveBeenCalledWith({
        desde: "2030-03-10", hasta: "2030-03-10", descripcion: "Día de la Vendimia", medicoId: undefined,
      }),
    );
    expect(await screen.findByText("Período cargado. No había turnos reservados en esas fechas.")).toBeInTheDocument();
  });

  it("carga la ausencia de un médico con su rango", async () => {
    vi.mocked(diasApi.crearDiaNoLaborable).mockResolvedValue(licencia);
    montar();
    await screen.findByRole("option", { name: "Pedro Prueba" });

    await completarFormulario("2030-03-12", "Licencia", "2030-03-15");
    await userEvent.selectOptions(screen.getByLabelText(/Médico \(opcional\)/), "Pedro Prueba");
    await userEvent.click(screen.getByRole("button", { name: /Cargar período/ }));

    await waitFor(() =>
      expect(diasApi.crearDiaNoLaborable).toHaveBeenCalledWith({
        desde: "2030-03-12", hasta: "2030-03-15", descripcion: "Licencia", medicoId: 7,
      }),
    );
  });

  it("si ya había turnos en esas fechas avisa, los muestra con el teléfono y NO los cancela", async () => {
    vi.mocked(diasApi.crearDiaNoLaborable).mockResolvedValue(vendimia);
    vi.mocked(diasApi.turnosAfectados).mockResolvedValue([afectado(), afectado({ id: 51, pacienteNombre: "Ana Paz", pacienteTelefono: null })]);
    montar();
    await screen.findByText("No hay feriados ni ausencias cargados.");

    await completarFormulario("2030-03-10", "Día de la Vendimia");
    await userEvent.click(screen.getByRole("button", { name: /Cargar período/ }));

    expect(await screen.findByText(/ya hay 2 turnos reservados en esas fechas/)).toBeInTheDocument();
    const panel = screen.getByRole("region", { name: "Turnos afectados" });
    expect(within(panel).getByText("Pablo Paciente")).toBeInTheDocument();
    expect(within(panel).getByText(/261-555-0101/)).toBeInTheDocument();
    expect(within(panel).getByText(/Sin teléfono cargado/)).toBeInTheDocument();
    expect(diasApi.cancelarTurnosAfectados).not.toHaveBeenCalled();
  });

  it("cancelar en bloque exige elegir el motivo y confirma antes de hacerlo", async () => {
    vi.mocked(diasApi.listarDiasNoLaborables).mockResolvedValue([vendimia]);
    vi.mocked(diasApi.turnosAfectados).mockResolvedValueOnce([afectado()]).mockResolvedValue([]);
    vi.mocked(diasApi.cancelarTurnosAfectados).mockResolvedValue(1);
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Ver turnos afectados/ }));
    const panel = await screen.findByRole("region", { name: "Turnos afectados" });

    // sin motivo no se cancela ni se pregunta nada
    await userEvent.click(within(panel).getByRole("button", { name: /Cancelar los 1 turno/ }));
    expect(await screen.findByText("Elegí el motivo de la cancelación.")).toBeInTheDocument();
    expect(window.confirm).not.toHaveBeenCalled();
    expect(diasApi.cancelarTurnosAfectados).not.toHaveBeenCalled();

    await userEvent.selectOptions(within(panel).getByLabelText("Motivo de cancelación"), "Médico no disponible");
    await userEvent.click(within(panel).getByRole("button", { name: /Cancelar los 1 turno/ }));

    await waitFor(() => expect(diasApi.cancelarTurnosAfectados).toHaveBeenCalledWith(1, 10));
    expect(window.confirm).toHaveBeenCalled();
    expect(await screen.findByText("Se cancelaron 1 turno.")).toBeInTheDocument();
    expect(await screen.findByText("No hay turnos pendientes en ese período.")).toBeInTheDocument();
  });

  it("si el que está en el mostrador no confirma, no se cancela nada", async () => {
    vi.mocked(window.confirm).mockReturnValue(false);
    vi.mocked(diasApi.listarDiasNoLaborables).mockResolvedValue([vendimia]);
    vi.mocked(diasApi.turnosAfectados).mockResolvedValue([afectado()]);
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Ver turnos afectados/ }));
    const panel = await screen.findByRole("region", { name: "Turnos afectados" });
    await userEvent.selectOptions(within(panel).getByLabelText("Motivo de cancelación"), "Médico no disponible");
    await userEvent.click(within(panel).getByRole("button", { name: /Cancelar los 1 turno/ }));

    expect(diasApi.cancelarTurnosAfectados).not.toHaveBeenCalled();
  });

  it("quitar un período pide confirmación, lo borra y recarga la lista", async () => {
    vi.mocked(diasApi.listarDiasNoLaborables).mockResolvedValueOnce([vendimia]).mockResolvedValue([]);
    vi.mocked(diasApi.eliminarDiaNoLaborable).mockResolvedValue();
    montar();

    await userEvent.click(await screen.findByRole("button", { name: "Quitar Día de la Vendimia" }));

    await waitFor(() => expect(diasApi.eliminarDiaNoLaborable).toHaveBeenCalledWith(1));
    expect(window.confirm).toHaveBeenCalled();
    expect(await screen.findByText("No hay feriados ni ausencias cargados.")).toBeInTheDocument();
  });

  it("muestra el error del servidor si el período no es válido", async () => {
    vi.mocked(diasApi.crearDiaNoLaborable).mockRejectedValue(errorApi(400, "Ese período ya terminó"));
    montar();
    await screen.findByText("No hay feriados ni ausencias cargados.");

    await completarFormulario("2030-03-10", "Algo");
    await userEvent.click(screen.getByRole("button", { name: /Cargar período/ }));

    expect(await screen.findByText("Ese período ya terminó")).toBeInTheDocument();
  });
});
