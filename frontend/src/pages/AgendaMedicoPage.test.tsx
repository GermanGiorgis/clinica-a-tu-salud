import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
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
  { id: 7, nombre: "Pedro", apellido: "Prueba", matricula: "MP-7", especialidades: [] },
  { id: 5, nombre: "Laura", apellido: "Lopez", matricula: "MP-5", especialidades: [] },
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

    it("un turno reservado se puede confirmar y cancelar, y todavía no se puede atender", async () => {
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ estado: "RESERVADO" })]);
      montar("MEDICO");

      expect(await screen.findByRole("button", { name: /Confirmar/ })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Cancelar/ })).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /Cargar consulta/ })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Marcar ausente/ })).not.toBeInTheDocument();
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
      vi.mocked(turnosApi.agendaMedico).mockResolvedValue([turno({ id: 4, estado: "CONFIRMADO" })]);
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
