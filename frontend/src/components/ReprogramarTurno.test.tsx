import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as medicosApi from "../api/medicos";
import * as turnosApi from "../api/turnos";
import { turno } from "../test/fixtures";
import { ReprogramarTurno } from "./ReprogramarTurno";

vi.mock("../api/medicos");
vi.mock("../api/turnos");

function errorApi(status: number, mensaje: string) {
  return new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, { status, data: { error: mensaje } } as AxiosResponse);
}

const manana = new Date(Date.now() + 86_400_000).toLocaleDateString("sv-SE");
const turnoDeManana = turno({ id: 9, medicoId: 7, medicoNombre: "Pedro Prueba", inicio: `${manana}T09:00:00`, fin: `${manana}T09:30:00`, estado: "CONFIRMADO" });

function montar(props: Partial<React.ComponentProps<typeof ReprogramarTurno>> = {}) {
  const onReprogramado = vi.fn();
  const onCerrar = vi.fn();
  render(<ReprogramarTurno turno={turnoDeManana} onReprogramado={onReprogramado} onCerrar={onCerrar} {...props} />);
  return { onReprogramado, onCerrar };
}

describe("ReprogramarTurno", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: manana, horariosLibres: ["10:00:00", "10:30:00"], aviso: null });
    vi.mocked(turnosApi.reprogramarTurno).mockResolvedValue(turno({ id: 9, inicio: `${manana}T10:30:00`, estado: "RESERVADO" }));
  });

  it("ofrece los horarios libres del mismo médico, empezando por el día del turno actual", async () => {
    montar();

    expect(await screen.findByRole("button", { name: "10:00" })).toBeInTheDocument();
    expect(screen.getByText(/con Pedro Prueba/)).toBeInTheDocument();
    expect(medicosApi.obtenerDisponibilidad).toHaveBeenCalledWith(7, manana);
    expect(screen.getByLabelText("Día")).toHaveValue(manana);
  });

  it("no se puede mover hasta elegir un horario", async () => {
    montar();

    await screen.findByRole("button", { name: "10:00" });

    expect(screen.getByRole("button", { name: "Elegí un horario" })).toBeDisabled();
  });

  it("mueve el turno al horario elegido y avisa con el turno ya movido", async () => {
    const { onReprogramado } = montar();

    await userEvent.click(await screen.findByRole("button", { name: "10:30" }));
    await userEvent.click(screen.getByRole("button", { name: /^Mover al/ }));

    await waitFor(() => expect(turnosApi.reprogramarTurno).toHaveBeenCalledWith(9, `${manana}T10:30:00`));
    expect(onReprogramado).toHaveBeenCalledWith(expect.objectContaining({ id: 9, estado: "RESERVADO" }));
  });

  it("avisa que el turno va a quedar reservado y hay que confirmarlo de nuevo", async () => {
    montar();

    expect(await screen.findByText(/hay que confirmarlo de nuevo/)).toBeInTheDocument();
  });

  it("si otra persona se llevó el horario muestra el error, vuelve a pedir la disponibilidad y deja elegir otro", async () => {
    vi.mocked(turnosApi.reprogramarTurno).mockRejectedValue(errorApi(409, "Ese horario ya no está disponible, elegí otro"));
    vi.mocked(medicosApi.obtenerDisponibilidad)
      .mockResolvedValueOnce({ fecha: manana, horariosLibres: ["10:00:00", "10:30:00"], aviso: null })
      .mockResolvedValue({ fecha: manana, horariosLibres: ["10:00:00"], aviso: null });
    const { onReprogramado } = montar();

    await userEvent.click(await screen.findByRole("button", { name: "10:30" }));
    await userEvent.click(screen.getByRole("button", { name: /^Mover al/ }));

    expect(await screen.findByText("Ese horario ya no está disponible, elegí otro")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("button", { name: "10:30" })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "10:00" })).toBeInTheDocument();
    expect(onReprogramado).not.toHaveBeenCalled();
  });

  it("un día cerrado muestra el aviso en vez de 'no hay horarios'", async () => {
    vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: manana, horariosLibres: [], aviso: "El médico no atiende ese día" });
    montar();

    expect(await screen.findByText("El médico no atiende ese día")).toBeInTheDocument();
    expect(screen.queryByText("No hay horarios libres ese día.")).not.toBeInTheDocument();
  });

  it("un día sin lugar lo dice", async () => {
    vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: manana, horariosLibres: [], aviso: null });
    montar();

    expect(await screen.findByText("No hay horarios libres ese día.")).toBeInTheDocument();
  });

  it("cambiar de día vuelve a pedir los horarios de ese día y borra la elección anterior", async () => {
    montar();
    await userEvent.click(await screen.findByRole("button", { name: "10:00" }));
    const otroDia = new Date(Date.now() + 3 * 86_400_000).toLocaleDateString("sv-SE");

    const dia = screen.getByLabelText("Día");
    await userEvent.clear(dia);
    await userEvent.type(dia, otroDia);

    await waitFor(() => expect(medicosApi.obtenerDisponibilidad).toHaveBeenLastCalledWith(7, otroDia));
    expect(screen.getByRole("button", { name: "Elegí un horario" })).toBeDisabled();
  });

  it("si un turno ya pasó, arranca desde hoy en vez de ofrecer un día vencido", async () => {
    montar({ turno: turno({ id: 9, medicoId: 7, inicio: "2020-01-06T09:00:00", fin: "2020-01-06T09:30:00" }) });

    await screen.findByRole("button", { name: "10:00" });

    expect(screen.getByLabelText("Día")).toHaveValue(new Date().toLocaleDateString("sv-SE"));
  });

  it("'No reprogramar' cierra sin llamar a la API", async () => {
    const { onCerrar } = montar();

    await userEvent.click(await screen.findByRole("button", { name: "No reprogramar" }));

    expect(onCerrar).toHaveBeenCalled();
    expect(turnosApi.reprogramarTurno).not.toHaveBeenCalled();
  });

  it("si no se puede calcular la disponibilidad lo dice", async () => {
    vi.mocked(medicosApi.obtenerDisponibilidad).mockRejectedValue(new Error("caído"));
    montar();

    expect(await screen.findByText("No se pudo calcular la disponibilidad.")).toBeInTheDocument();
  });
});
