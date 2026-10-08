import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as medicosApi from "../api/medicos";
import * as motivosApi from "../api/motivosCancelacion";
import * as turnosApi from "../api/turnos";
import { turno, usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import type { Rol } from "../types";
import { AgendaMedicoPage } from "./AgendaMedicoPage";

vi.mock("../api/turnos");
vi.mock("../api/medicos");
vi.mock("../api/motivosCancelacion");

const motivos = [{ id: 10, nombre: "Médico no disponible", descripcion: null }];
const medicos = [
  { id: 7, nombre: "Pedro", apellido: "Prueba", matricula: "MP-7", especialidades: [], activo: true },
  { id: 5, nombre: "Laura", apellido: "Lopez", matricula: "MP-5", especialidades: [], activo: true },
];

function montar(rol: Rol) {
  renderConSesion(<AgendaMedicoPage />, { usuario: usuario(rol), ruta: "/agenda", path: "/agenda" });
}

describe("AgendaMedicoPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(motivosApi.listarMotivosCancelacion).mockResolvedValue(motivos);
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue(medicos);
    vi.mocked(turnosApi.agendaMedico).mockResolvedValue([]);
  });

  describe("como médico", () => {
    it("carga su propia agenda sin pedir elegir médico", async () => {
      montar("MEDICO");

      expect(await screen.findByText("No hay turnos para mostrar.")).toBeInTheDocument();
      expect(turnosApi.agendaMedico).toHaveBeenCalledWith(undefined, undefined);
      expect(screen.queryByLabelText("Médico")).not.toBeInTheDocument();
      expect(medicosApi.listarMedicos).not.toHaveBeenCalled();
    });

    it("muestra al paciente, el consultorio, la cobertura y el motivo de cada turno", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([
        turno({ pacienteNombre: "Pablo Paciente", consultorioNombre: "Consultorio B", obraSocialNombre: "OSDE", motivoConsulta: "Dolor de cabeza" }),
      ]);
      montar("MEDICO");

      expect(await screen.findByText("Pablo Paciente")).toBeInTheDocument();
      expect(screen.getByText("Consultorio B")).toBeInTheDocument();
      expect(screen.getByText("OSDE")).toBeInTheDocument();
      expect(screen.getByText(/Dolor de cabeza/)).toBeInTheDocument();
    });

    it("si no puede cargar la agenda lo avisa", async () => {
      vi.mocked(turnosApi.agendaMedico).mockRejectedValue(new Error("caído"));
      montar("MEDICO");

      expect(await screen.findByText("No se pudo cargar la agenda.")).toBeInTheDocument();
    });

    it("un turno reservado se puede confirmar y cancelar, y todavía no se puede atender ni dar por ausente", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ estado: "RESERVADO" })]);
      montar("MEDICO");

      expect(await screen.findByRole("button", { name: /Confirmar/ })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Cancelar/ })).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /Cargar consulta/ })).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Marcar ausente/ })).toBeDisabled();   // el turno es en 2030: falta mucho
    });

    it("un turno confirmado permite cargar la consulta, marcar ausente o cancelar", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 3, estado: "CONFIRMADO" })]);
      montar("MEDICO");

      expect(await screen.findByRole("link", { name: /Cargar consulta/ })).toHaveAttribute("href", "/turnos/3/consulta");
      expect(screen.getByRole("button", { name: /Marcar ausente/ })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Cancelar/ })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Confirmar/ })).not.toBeInTheDocument();
    });

    it("un turno atendido permite ver la consulta y facturar, pero no cancelar", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 3, estado: "ATENDIDO" })]);
      montar("MEDICO");

      expect(await screen.findByRole("link", { name: /Ver consulta/ })).toHaveAttribute("href", "/turnos/3/consulta");
      expect(screen.getByRole("link", { name: /Facturar/ })).toHaveAttribute("href", "/turnos/3/factura");
      expect(screen.queryByRole("button", { name: /Cancelar/ })).not.toBeInTheDocument();
    });

    it.each(["AUSENTE", "CANCELADO"] as const)("un turno %s no ofrece ninguna acción sobre el turno", async (estado) => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ estado })]);
      montar("MEDICO");

      await screen.findByText(estado === "AUSENTE" ? "Ausente" : "Cancelado");

      expect(screen.queryByRole("button")).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /consulta|Facturar/ })).not.toBeInTheDocument();
    });

    it("cada turno tiene un enlace al historial del paciente", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ pacienteId: 3, estado: "CANCELADO" })]);
      montar("MEDICO");

      expect(await screen.findByRole("link", { name: /Historial/ })).toHaveAttribute("href", "/pacientes/3/historial");
    });

    it("confirmar llama a la API y recarga la agenda", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, estado: "RESERVADO" })]);
      vi.mocked(turnosApi.confirmarTurno).mockResolvedValue(turno({ id: 4, estado: "CONFIRMADO" }));
      montar("MEDICO");

      await userEvent.click(await screen.findByRole("button", { name: /Confirmar/ }));

      await waitFor(() => expect(turnosApi.confirmarTurno).toHaveBeenCalledWith(4));
      await waitFor(() => expect(turnosApi.agendaMedico).toHaveBeenCalledTimes(2));
    });

    it("marcar ausente llama a la API y recarga la agenda", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, estado: "CONFIRMADO", inicio: "2020-01-10T11:00:00" })]);
      vi.mocked(turnosApi.marcarAusente).mockResolvedValue(turno({ id: 4, estado: "AUSENTE" }));
      montar("MEDICO");

      await userEvent.click(await screen.findByRole("button", { name: /Marcar ausente/ }));

      await waitFor(() => expect(turnosApi.marcarAusente).toHaveBeenCalledWith(4));
      await waitFor(() => expect(turnosApi.agendaMedico).toHaveBeenCalledTimes(2));
    });

    it("el motivo de cancelación no viene preseleccionado, y sin elegirlo no se cancela ni se pregunta nada", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, estado: "RESERVADO" })]);
      montar("MEDICO");

      expect(await screen.findByRole("combobox", { name: "Motivo de cancelación" })).toHaveValue("");
      await userEvent.click(screen.getByRole("button", { name: /Cancelar/ }));

      expect(await screen.findByText("Elegí el motivo de la cancelación.")).toBeInTheDocument();
      expect(turnosApi.cancelarTurno).not.toHaveBeenCalled();
      expect(window.confirm).not.toHaveBeenCalled();
    });

    it("cancelar con el motivo elegido pide confirmación y recarga la agenda", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, estado: "RESERVADO" })]);
      vi.mocked(turnosApi.cancelarTurno).mockResolvedValue(turno({ id: 4, estado: "CANCELADO" }));
      montar("MEDICO");

      await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Motivo de cancelación" }), "Médico no disponible");
      await userEvent.click(screen.getByRole("button", { name: /Cancelar/ }));

      expect(window.confirm).toHaveBeenCalledTimes(1);
      await waitFor(() => expect(turnosApi.cancelarTurno).toHaveBeenCalledWith(4, 10));
      await waitFor(() => expect(turnosApi.agendaMedico).toHaveBeenCalledTimes(2));
    });

    it("si en la confirmación se responde que no, el turno no se cancela", async () => {
      vi.mocked(window.confirm).mockReturnValue(false);
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, estado: "RESERVADO" })]);
      montar("MEDICO");

      await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Motivo de cancelación" }), "Médico no disponible");
      await userEvent.click(screen.getByRole("button", { name: /Cancelar/ }));

      expect(turnosApi.cancelarTurno).not.toHaveBeenCalled();
    });

    it("si la API rechaza la acción muestra su mensaje", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, estado: "RESERVADO" })]);
      const respuesta = { status: 403, data: { error: "No podés gestionar este turno" } } as AxiosResponse;
      vi.mocked(turnosApi.confirmarTurno).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
      montar("MEDICO");

      await userEvent.click(await screen.findByRole("button", { name: /Confirmar/ }));

      expect(await screen.findByText("No podés gestionar este turno")).toBeInTheDocument();
    });
  });

  describe("filtro por día", () => {
    it("al principio pide los próximos turnos, sin fecha", async () => {
      montar("MEDICO");

      await screen.findByText("No hay turnos para mostrar.");

      expect(turnosApi.agendaMedico).toHaveBeenLastCalledWith(undefined, undefined);
      expect(screen.queryByRole("button", { name: "Ver los próximos turnos" })).not.toBeInTheDocument();
    });

    it("elegir un día pide la agenda de ese día, también si ya pasó", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ estado: "CONFIRMADO", pacienteNombre: "Pablo Ayer" })]);
      montar("MEDICO");
      await screen.findByText("Pablo Ayer");

      await userEvent.type(screen.getByLabelText("Día"), "2026-10-01");

      await waitFor(() => expect(turnosApi.agendaMedico).toHaveBeenLastCalledWith(undefined, "2026-10-01"));
    });

    it("con un día elegido, si no hay turnos lo dice y se puede volver a los próximos", async () => {
      montar("MEDICO");
      await userEvent.type(await screen.findByLabelText("Día"), "2026-10-01");

      expect(await screen.findByText("No hay turnos ese día.")).toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "Ver los próximos turnos" }));

      await waitFor(() => expect(turnosApi.agendaMedico).toHaveBeenLastCalledWith(undefined, undefined));
      expect(screen.getByLabelText("Día")).toHaveValue("");
    });

    it("el admin combina el médico elegido con el día", async () => {
      montar("ADMIN");
      await screen.findByRole("option", { name: "Pedro Prueba" });
      await userEvent.selectOptions(screen.getByLabelText("Médico"), "Pedro Prueba");
      await userEvent.type(screen.getByLabelText("Día"), "2026-10-01");

      await waitFor(() => expect(turnosApi.agendaMedico).toHaveBeenLastCalledWith(7, "2026-10-01"));
    });
  });

  describe("tolerancia para marcar ausente", () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    function turnoDeLas10(estado: "RESERVADO" | "CONFIRMADO") {
      return turno({ id: 4, estado, inicio: "2030-03-04T10:00:00", fin: "2030-03-04T10:30:00" });
    }

    it("antes de los 20 minutos el botón está deshabilitado y explica por qué", async () => {
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(new Date("2030-03-04T10:19:59"));
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turnoDeLas10("CONFIRMADO")]);
      montar("MEDICO");

      const boton = await screen.findByRole("button", { name: /Marcar ausente/ });
      expect(boton).toBeDisabled();
      expect(boton).toHaveAttribute("title", expect.stringContaining("20 minutos"));
    });

    it("a los 20 minutos justos se habilita", async () => {
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(new Date("2030-03-04T10:20:00"));
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turnoDeLas10("CONFIRMADO")]);
      montar("MEDICO");

      expect(await screen.findByRole("button", { name: /Marcar ausente/ })).toBeEnabled();
    });

    it("un turno que nunca se confirmó también se puede dar por ausente pasada la tolerancia", async () => {
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(new Date("2030-03-04T12:00:00"));
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turnoDeLas10("RESERVADO")]);
      montar("MEDICO");

      expect(await screen.findByRole("button", { name: /Marcar ausente/ })).toBeEnabled();
    });
  });

  describe("reprogramar (personal)", () => {
    it("el médico puede reprogramar un turno pendiente de su agenda y la agenda se recarga", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, medicoId: 7, estado: "CONFIRMADO" })]);
      vi.mocked(medicosApi.obtenerDisponibilidad).mockResolvedValue({ fecha: "2030-01-10", horariosLibres: ["12:00:00"], aviso: null });
      vi.mocked(turnosApi.reprogramarTurno).mockResolvedValue(turno({ id: 4, estado: "RESERVADO" }));
      montar("MEDICO");

      await userEvent.click(await screen.findByRole("button", { name: /Reprogramar/ }));
      await userEvent.click(await screen.findByRole("button", { name: "12:00" }));
      await userEvent.click(screen.getByRole("button", { name: /^Mover al/ }));

      await waitFor(() => expect(turnosApi.reprogramarTurno).toHaveBeenCalledWith(4, "2030-01-10T12:00:00"));
      await waitFor(() => expect(turnosApi.agendaMedico).toHaveBeenCalledTimes(2));
      expect(screen.queryByRole("region", { name: "Reprogramar turno" })).not.toBeInTheDocument();
    });

    it("un turno atendido, ausente o cancelado no se puede reprogramar", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([
        turno({ id: 1, estado: "ATENDIDO" }), turno({ id: 2, estado: "AUSENTE" }), turno({ id: 3, estado: "CANCELADO" }),
      ]);
      montar("MEDICO");

      await screen.findByText("Atendido");

      expect(screen.queryByRole("button", { name: /Reprogramar/ })).not.toBeInTheDocument();
    });
  });

  describe("como recepcionista", () => {
    it("al elegir médico se piden también los dados de baja y se marcan, para poder cancelarles los turnos que quedaron", async () => {
      vi.mocked(medicosApi.listarMedicos).mockResolvedValue([
        { id: 7, nombre: "Pedro", apellido: "Prueba", matricula: "MP-7", especialidades: [], activo: true },
        { id: 9, nombre: "Baja", apellido: "Pérez", matricula: "MP-9", especialidades: [], activo: false },
      ]);
      montar("RECEPCIONISTA");

      expect(await screen.findByRole("option", { name: "Baja Pérez (dado de baja)" })).toBeInTheDocument();
      expect(screen.getByRole("option", { name: "Pedro Prueba" })).toBeInTheDocument();
      expect(medicosApi.listarMedicos).toHaveBeenCalledWith(undefined, true);
    });

    it("elige el médico igual que el admin y carga su agenda", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ pacienteNombre: "Pablo Paciente" })]);
      montar("RECEPCIONISTA");

      expect(await screen.findByText("Elegí un médico para ver su agenda.")).toBeInTheDocument();
      await screen.findByRole("option", { name: "Pedro Prueba" });
      await userEvent.selectOptions(screen.getByLabelText("Médico"), "Pedro Prueba");

      expect(await screen.findByText("Pablo Paciente")).toBeInTheDocument();
      expect(turnosApi.agendaMedico).toHaveBeenCalledWith(7, undefined);
    });

    it("maneja los turnos (confirmar, cancelar) pero no ve nada de la parte clínica", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([
        turno({ id: 1, estado: "RESERVADO" }),
        turno({ id: 2, estado: "CONFIRMADO", inicio: "2020-01-10T09:00:00" }),
        turno({ id: 3, estado: "ATENDIDO", inicio: "2020-01-09T09:00:00" }),
      ]);
      montar("RECEPCIONISTA");
      await screen.findByRole("option", { name: "Pedro Prueba" });
      await userEvent.selectOptions(screen.getByLabelText("Médico"), "Pedro Prueba");

      expect(await screen.findAllByRole("button", { name: /Cancelar/ })).toHaveLength(2);
      expect(screen.getByRole("button", { name: /Confirmar/ })).toBeInTheDocument();
      expect(screen.getAllByRole("button", { name: /Marcar ausente/ })).toHaveLength(2);
      expect(screen.queryByRole("link", { name: /Historial/ })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /consulta/i })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /Facturar/ })).not.toBeInTheDocument();
    });
  });

  describe("como admin", () => {
    it("primero pide elegir un médico y no consulta ninguna agenda todavía", async () => {
      montar("ADMIN");

      expect(await screen.findByText("Elegí un médico para ver su agenda.")).toBeInTheDocument();
      expect(turnosApi.agendaMedico).not.toHaveBeenCalled();
      expect(await screen.findByRole("option", { name: "Pedro Prueba" })).toBeInTheDocument();
    });

    it("al elegir un médico carga la agenda de ese médico", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ pacienteNombre: "Pablo Paciente" })]);
      montar("ADMIN");

      await screen.findByRole("option", { name: "Pedro Prueba" });
      await userEvent.selectOptions(screen.getByLabelText("Médico"), "Pedro Prueba");

      expect(await screen.findByText("Pablo Paciente")).toBeInTheDocument();
      expect(turnosApi.agendaMedico).toHaveBeenCalledWith(7, undefined);
    });

    it("si no puede cargar la lista de médicos lo avisa", async () => {
      vi.mocked(medicosApi.listarMedicos).mockRejectedValue(new Error("caído"));
      montar("ADMIN");

      expect(await screen.findByText("No se pudo cargar la lista de médicos.")).toBeInTheDocument();
    });
  });
});
