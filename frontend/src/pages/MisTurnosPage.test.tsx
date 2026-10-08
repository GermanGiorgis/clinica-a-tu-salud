import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as encuestasApi from "../api/encuestas";
import * as medicosApi from "../api/medicos";
import * as motivosApi from "../api/motivosCancelacion";
import * as turnosApi from "../api/turnos";
import { turno, usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import { MisTurnosPage } from "./MisTurnosPage";

vi.mock("../api/turnos");
vi.mock("../api/medicos");
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
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(motivosApi.listarMotivosCancelacion).mockResolvedValue(motivos);
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([]);
    vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: "2030-01-10", horariosLibres: ["12:00:00", "12:30:00"], aviso: null });
  });

  it("muestra primero los turnos que vienen (el más próximo arriba) y después el historial, del más reciente al más viejo", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([
      turno({ id: 1, medicoNombre: "Medico Cancelado", inicio: "2030-01-20T09:00:00", estado: "CANCELADO", motivoCancelacionNombre: "x" }),
      turno({ id: 2, medicoNombre: "Medico Lejano", inicio: "2030-03-01T09:00:00", estado: "RESERVADO" }),
      turno({ id: 3, medicoNombre: "Medico Viejo", inicio: "2029-05-01T09:00:00", estado: "ATENDIDO" }),
      turno({ id: 4, medicoNombre: "Medico Proximo", inicio: "2030-01-05T09:00:00", estado: "CONFIRMADO" }),
      turno({ id: 5, medicoNombre: "Medico Reciente", inicio: "2029-12-01T09:00:00", estado: "AUSENTE" }),
    ]);
    vi.mocked(encuestasApi.obtenerEncuesta).mockResolvedValue(null as never);
    montar();

    await screen.findByText("Medico Proximo");
    const orden = screen.getAllByRole("listitem").map((li) => li.querySelector("strong")?.textContent);

    expect(orden).toEqual(["Medico Proximo", "Medico Lejano", "Medico Cancelado", "Medico Reciente", "Medico Viejo"]);
  });

  describe("reprogramar", () => {
    it("se ofrece en un turno reservado o confirmado, y no en uno atendido, ausente o cancelado", async () => {
      vi.mocked(encuestasApi.obtenerEncuesta).mockResolvedValue({ id: 1, puntaje: 5, comentario: null, creadaEn: "2026-10-01T10:00:00" });
      vi.mocked(turnosApi.misTurnos).mockResolvedValue([
        turno({ id: 1, estado: "RESERVADO" }), turno({ id: 2, estado: "CONFIRMADO" }),
        turno({ id: 3, estado: "ATENDIDO" }), turno({ id: 4, estado: "AUSENTE" }), turno({ id: 5, estado: "CANCELADO" }),
      ]);
      montar();

      await screen.findAllByRole("button", { name: /Reprogramar/ });

      expect(screen.getAllByRole("button", { name: /Reprogramar/ })).toHaveLength(2);
    });

    it("abre los horarios libres del mismo médico y, al elegir uno, mueve el turno, avisa y recarga la lista", async () => {
      vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 8, medicoId: 7, estado: "CONFIRMADO" })]);
      vi.mocked(turnosApi.reprogramarTurno).mockResolvedValue(turno({ id: 8, inicio: "2030-01-10T12:30:00", estado: "RESERVADO" }));
      montar();

      await userEvent.click(await screen.findByRole("button", { name: /Reprogramar/ }));
      await userEvent.click(await screen.findByRole("button", { name: "12:30" }));
      await userEvent.click(screen.getByRole("button", { name: /^Mover al/ }));

      await waitFor(() => expect(turnosApi.reprogramarTurno).toHaveBeenCalledWith(8, "2030-01-10T12:30:00"));
      expect(medicosApi.obtenerDisponibilidad).toHaveBeenCalledWith(7, "2030-01-10");
      expect(await screen.findByText(/Listo: tu turno pasó al/)).toBeInTheDocument();
      expect(screen.getByText(/confirmalo de nuevo/)).toBeInTheDocument();
      expect(turnosApi.misTurnos).toHaveBeenCalledTimes(2);
      expect(screen.queryByRole("region", { name: "Reprogramar turno" })).not.toBeInTheDocument();
    });

    it("se puede abrir y cerrar sin mover nada", async () => {
      vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 8, estado: "RESERVADO" })]);
      montar();

      await userEvent.click(await screen.findByRole("button", { name: /Reprogramar/ }));
      expect(await screen.findByRole("region", { name: "Reprogramar turno" })).toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "No reprogramar" }));

      expect(screen.queryByRole("region", { name: "Reprogramar turno" })).not.toBeInTheDocument();
      expect(turnosApi.reprogramarTurno).not.toHaveBeenCalled();
    });

    it("si el turno es dentro de menos de 2 horas, el servidor lo rechaza y se muestra el motivo en el panel", async () => {
      vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 8, estado: "RESERVADO" })]);
      vi.mocked(turnosApi.reprogramarTurno).mockRejectedValue(errorApi(400, "Para reprogramar necesitás al menos 2 horas de anticipación"));
      montar();

      await userEvent.click(await screen.findByRole("button", { name: /Reprogramar/ }));
      await userEvent.click(await screen.findByRole("button", { name: "12:00" }));
      await userEvent.click(screen.getByRole("button", { name: /^Mover al/ }));

      expect(await screen.findByText("Para reprogramar necesitás al menos 2 horas de anticipación")).toBeInTheDocument();
    });
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

  it("el motivo de cancelación no viene preseleccionado: hay que elegirlo", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "RESERVADO" })]);
    montar();

    const selector = await screen.findByRole("combobox", { name: "Motivo de cancelación" });

    expect(selector).toHaveValue("");
    expect(screen.getByRole("option", { name: "Motivo de cancelación…" })).toBeInTheDocument();
  });

  it("cancelar sin haber elegido un motivo lo pide y no llama a la API ni pregunta nada", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "RESERVADO" })]);
    montar();

    await userEvent.click(await screen.findByRole("button", { name: /Cancelar/ }));

    expect(await screen.findByText("Elegí el motivo de la cancelación.")).toBeInTheDocument();
    expect(turnosApi.cancelarTurno).not.toHaveBeenCalled();
    expect(window.confirm).not.toHaveBeenCalled();
  });

  it("cancelar con el motivo elegido pide confirmación y, si se acepta, cancela con ese motivo y recarga", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "RESERVADO" })]);
    vi.mocked(turnosApi.cancelarTurno).mockResolvedValue(turno({ id: 7, estado: "CANCELADO" }));
    montar();

    await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Motivo de cancelación" }), "Me siento mejor");
    await userEvent.click(screen.getByRole("button", { name: /Cancelar/ }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(turnosApi.cancelarTurno).toHaveBeenCalledWith(7, 11));
    await waitFor(() => expect(turnosApi.misTurnos).toHaveBeenCalledTimes(2));
  });

  it("si en la confirmación se responde que no, el turno no se cancela", async () => {
    vi.mocked(window.confirm).mockReturnValue(false);
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "RESERVADO" })]);
    montar();

    await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Motivo de cancelación" }), "Me siento mejor");
    await userEvent.click(screen.getByRole("button", { name: /Cancelar/ }));

    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(turnosApi.cancelarTurno).not.toHaveBeenCalled();
  });

  it("si la API rechaza la cancelación (menos de 2 horas) muestra el motivo y no recarga", async () => {
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([turno({ id: 7, estado: "CONFIRMADO" })]);
    vi.mocked(turnosApi.cancelarTurno).mockRejectedValue(errorApi(400, "Solo se puede cancelar con 2 horas de anticipación"));
    montar();

    await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Motivo de cancelación" }), "Me siento mejor");
    await userEvent.click(screen.getByRole("button", { name: /Cancelar/ }));

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
