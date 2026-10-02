import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as encuestasApi from "../api/encuestas";
import * as motivosApi from "../api/motivosCancelacion";
import * as turnosApi from "../api/turnos";
import { turno, usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import { MisTurnosPage } from "./MisTurnosPage";

vi.mock("../api/turnos");
vi.mock("../api/motivosCancelacion");
vi.mock("../api/encuestas");

const motivos = [
  { id: 10, nombre: "Me surgió un imprevisto", descripcion: null },
  { id: 11, nombre: "Me siento mejor", descripcion: null },
];

function errorApi(status: number, mensaje: string) {
  return new AxiosError("fallo", "ERR_BAD_REQUEST", undefined, undefined, { status, data: { error: mensaje } } as AxiosResponse);
}

function montar() {
  renderConSesion(<MisTurnosPage />, { usuario: usuario("PACIENTE"), ruta: "/mis-turnos", path: "/mis-turnos" });
}

describe("MisTurnosPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(motivosApi.listarMotivosCancelacion).mockResolvedValue(motivos);
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([]);
  });

  it("muestra el mensaje de vacío cuando todavía no reservó nada", async () => {
    montar();

    expect(await screen.findByText("Todavía no reservaste ningún turno.")).toBeInTheDocument();
  });

  it("si no se pueden cargar los turnos avisa con un mensaje", async () => {
    vi.mocked(turnosApi.misTurnos).mockRejectedValue(new Error("caído"));
    montar();

    expect(await screen.findByText("No se pudieron cargar tus turnos.")).toBeInTheDocument();
  });

  it("lista cada turno con su médico, consultorio, cobertura y estado", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([
      turno({ id: 1, medicoNombre: "Pedro Prueba", consultorioNombre: "Consultorio B", obraSocialNombre: "OSDE", motivoConsulta: "Control", estado: "RESERVADO" }),
      turno({ id: 2, medicoNombre: "Laura Lopez", consultorioNombre: "Consultorio A", estado: "CANCELADO", motivoCancelacionNombre: "Me siento mejor" }),
    ]);
    montar();

    expect(await screen.findByText("Pedro Prueba")).toBeInTheDocument();
    expect(screen.getByText("Consultorio B")).toBeInTheDocument();
    expect(screen.getByText("OSDE")).toBeInTheDocument();
    expect(screen.getByText(/Control/)).toBeInTheDocument();
    expect(screen.getByText("Reservado")).toBeInTheDocument();
    expect(screen.getByText("Laura Lopez")).toBeInTheDocument();
    expect(screen.getByText("Cancelado")).toBeInTheDocument();
    expect(screen.getByText(/Cancelado por: Me siento mejor/)).toBeInTheDocument();
    expect(screen.getByText("Particular")).toBeInTheDocument();
  });

  it("un turno reservado se puede confirmar o cancelar; uno confirmado solo cancelar", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([
      turno({ id: 1, estado: "RESERVADO" }),
      turno({ id: 2, estado: "CONFIRMADO" }),
    ]);
    montar();

    await screen.findAllByRole("button", { name: /Cancelar/ });

    expect(screen.getAllByRole("button", { name: /Confirmar/ })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: /Cancelar/ })).toHaveLength(2);
  });

  it("un turno atendido, ausente o cancelado no ofrece acciones de gestión", async () => {
    vi.mocked(encuestasApi.obtenerEncuesta).mockResolvedValue({ id: 1, puntaje: 5, comentario: null, creadaEn: "2026-10-01T10:00:00" });
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([
      turno({ id: 1, estado: "ATENDIDO" }),
      turno({ id: 2, estado: "AUSENTE" }),
      turno({ id: 3, estado: "CANCELADO" }),
    ]);
    montar();

    await screen.findByText("Atendido");

    expect(screen.queryByRole("button", { name: /Confirmar/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Cancelar/ })).not.toBeInTheDocument();
  });

  it("cancelar usa el primer motivo por defecto y recarga la lista", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "RESERVADO" })]);
    vi.mocked(turnosApi.cancelarTurno).mockResolvedValue(turno({ id: 7, estado: "CANCELADO" }));
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Cancelar/ }));

    await waitFor(() => expect(turnosApi.cancelarTurno).toHaveBeenCalledWith(7, 10));
    await waitFor(() => expect(turnosApi.misTurnos).toHaveBeenCalledTimes(2));
  });

  it("cancelar usa el motivo que el paciente eligió", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "RESERVADO" })]);
    vi.mocked(turnosApi.cancelarTurno).mockResolvedValue(turno({ id: 7, estado: "CANCELADO" }));
    montar();

    await userEvent.selectOptions(await screen.findByRole("combobox"), "Me siento mejor");
    await userEvent.click(screen.getByRole("button", { name: /Cancelar/ }));

    await waitFor(() => expect(turnosApi.cancelarTurno).toHaveBeenCalledWith(7, 11));
  });

  it("si la API rechaza la cancelación (menos de 2 horas) muestra el motivo y no recarga", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "CONFIRMADO" })]);
    vi.mocked(turnosApi.cancelarTurno).mockRejectedValue(errorApi(400, "Solo se puede cancelar con 2 horas de anticipación"));
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Cancelar/ }));

    expect(await screen.findByText("Solo se puede cancelar con 2 horas de anticipación")).toBeInTheDocument();
    expect(turnosApi.misTurnos).toHaveBeenCalledTimes(1);
  });

  it("si todavía no hay motivos de cancelación cargados no llama a la API y lo explica", async () => {
    vi.mocked(motivosApi.listarMotivosCancelacion).mockResolvedValue([]);
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "RESERVADO" })]);
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Cancelar/ }));

    expect(await screen.findByText("Todavía no hay motivos de cancelación cargados.")).toBeInTheDocument();
    expect(turnosApi.cancelarTurno).not.toHaveBeenCalled();
  });

  it("confirmar llama a la API con el turno y recarga", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 4, estado: "RESERVADO" })]);
    vi.mocked(turnosApi.confirmarTurno).mockResolvedValue(turno({ id: 4, estado: "CONFIRMADO" }));
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Confirmar/ }));

    await waitFor(() => expect(turnosApi.confirmarTurno).toHaveBeenCalledWith(4));
    await waitFor(() => expect(turnosApi.misTurnos).toHaveBeenCalledTimes(2));
  });

  describe("encuesta de satisfacción", () => {
    beforeEach(() => {
      vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 9, estado: "ATENDIDO" })]);
    });

    it("un turno atendido sin encuesta ofrece el formulario y manda 5 estrellas por defecto", async () => {
      vi.mocked(encuestasApi.obtenerEncuesta).mockRejectedValue(errorApi(404, "Todavía no hay encuesta"));
      vi.mocked(encuestasApi.crearEncuesta).mockResolvedValue({ id: 1, puntaje: 5, comentario: null, creadaEn: "2026-10-01T10:00:00" });
      montar();

      await userEvent.click(await screen.findByRole("button", { name: "Enviar" }));

      await waitFor(() => expect(encuestasApi.crearEncuesta).toHaveBeenCalledWith(9, 5, undefined));
    });

    it("manda el puntaje elegido y el comentario, y después muestra la encuesta sin poder reenviarla", async () => {
      vi.mocked(encuestasApi.obtenerEncuesta).mockRejectedValue(errorApi(404, "Todavía no hay encuesta"));
      vi.mocked(encuestasApi.crearEncuesta).mockResolvedValue({ id: 1, puntaje: 3, comentario: "Buena atención", creadaEn: "2026-10-01T10:00:00" });
      montar();

      await userEvent.click(await screen.findByRole("button", { name: "3 estrellas" }));
      await userEvent.type(screen.getByPlaceholderText("Comentario (opcional)"), "Buena atención");
      await userEvent.click(screen.getByRole("button", { name: "Enviar" }));

      await waitFor(() => expect(encuestasApi.crearEncuesta).toHaveBeenCalledWith(9, 3, "Buena atención"));
      expect(await screen.findByText(/Buena atención/)).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Enviar" })).not.toBeInTheDocument();
    });

    it("si ya respondió la encuesta la muestra de solo lectura", async () => {
      vi.mocked(encuestasApi.obtenerEncuesta).mockResolvedValue({ id: 1, puntaje: 4, comentario: "Todo bien", creadaEn: "2026-10-01T10:00:00" });
      montar();

      expect(await screen.findByText(/Todo bien/)).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Enviar" })).not.toBeInTheDocument();
      expect(encuestasApi.crearEncuesta).not.toHaveBeenCalled();
    });

    it("si el envío falla muestra el error de la API", async () => {
      vi.mocked(encuestasApi.obtenerEncuesta).mockRejectedValue(errorApi(404, "Todavía no hay encuesta"));
      vi.mocked(encuestasApi.crearEncuesta).mockRejectedValue(errorApi(409, "Ese turno ya tiene una encuesta"));
      montar();

      await userEvent.click(await screen.findByRole("button", { name: "Enviar" }));

      expect(await screen.findByText("Ese turno ya tiene una encuesta")).toBeInTheDocument();
    });
  });
});
