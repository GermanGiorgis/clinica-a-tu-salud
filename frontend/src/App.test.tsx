import { cleanup, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import * as auditoriaApi from "./api/auditoria";
import * as diasApi from "./api/diasNoLaborables";
import * as usuariosApi from "./api/usuarios";
import * as historialApi from "./api/historial";
import * as pacientesApi from "./api/pacientes";
import * as metricasApi from "./api/metricas";
import * as turnosApi from "./api/turnos";
import * as medicosApi from "./api/medicos";
import * as especialidadesApi from "./api/especialidades";
import * as motivosApi from "./api/motivosCancelacion";
import * as afiliacionesApi from "./api/afiliaciones";
import * as patologiasApi from "./api/patologias";
import { metricas, usuario } from "./test/fixtures";
import type { Rol } from "./types";

vi.mock("./api/auditoria");
vi.mock("./api/diasNoLaborables");
vi.mock("./api/usuarios");
vi.mock("./api/pacientes");
vi.mock("./api/historial");
vi.mock("./api/metricas");
vi.mock("./api/turnos");
vi.mock("./api/medicos");
vi.mock("./api/especialidades");
vi.mock("./api/motivosCancelacion");
vi.mock("./api/afiliaciones");
vi.mock("./api/patologias");

const TITULO_INICIO = "Gestión de turnos y atención médica";

/** App usa BrowserRouter, así que se navega con la URL real de jsdom. */
function abrir(ruta: string, rol?: Rol) {
  if (rol) {
    localStorage.setItem("usuario", JSON.stringify(usuario(rol)));
    localStorage.setItem("token", "token-de-prueba");
  }
  window.history.pushState({}, "", ruta);
  render(<App />);
}

describe("rutas de la aplicación y quién puede entrar a cada una", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas());
    vi.mocked(pacientesApi.pacientesPendientes).mockResolvedValue([]);
    vi.mocked(auditoriaApi.accesosHistorial).mockResolvedValue([]);
    vi.mocked(diasApi.listarDiasNoLaborables).mockResolvedValue([]);
    vi.mocked(usuariosApi.listarUsuarios).mockResolvedValue([]);
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([]);
    vi.mocked(historialApi.miHistorial).mockResolvedValue([]);
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([]);
    vi.mocked(turnosApi.agendaMedico).mockResolvedValue([]);
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([]);
    vi.mocked(especialidadesApi.listarEspecialidades).mockResolvedValue([{ id: 2, nombre: "Dermatología", descripcion: null }]);
    vi.mocked(motivosApi.listarMotivosCancelacion).mockResolvedValue([]);
    vi.mocked(afiliacionesApi.misAfiliaciones).mockResolvedValue([]);
    vi.mocked(patologiasApi.listarPatologias).mockResolvedValue([]);
  });

  it("el inicio es público", () => {
    abrir("/");

    expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
  });

  it("una ruta que no existe vuelve al inicio", () => {
    abrir("/no-existe");

    expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
  });

  it("las especialidades son públicas, y al abrir una se ve su lista de médicos", async () => {
    abrir("/medicos");

    expect(await screen.findByRole("heading", { name: "Especialidades", level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /Dermatología/ })).toBeInTheDocument();
    cleanup();

    abrir("/medicos/especialidad/2");
    expect(await screen.findByRole("heading", { name: "Dermatología", level: 1 })).toBeInTheDocument();
    expect(medicosApi.listarMedicos).toHaveBeenLastCalledWith(2);
  });

  describe("sin sesión, las pantallas privadas mandan a iniciar sesión", () => {
    it.each(["/mis-turnos", "/agenda", "/historial", "/mi-cobertura", "/metricas", "/pacientes/pendientes", "/dias-no-laborables", "/usuarios", "/auditoria", "/pacientes/3/historial", "/turnos/1/consulta", "/turnos/1/factura"])(
      "%s",
      (ruta) => {
        abrir(ruta);

        expect(screen.getByRole("heading", { name: "Iniciar sesión" })).toBeInTheDocument();
      },
    );
  });

  describe("métricas: solo admin", () => {
    it("el admin entra y ve las métricas", async () => {
      abrir("/metricas", "ADMIN");

      expect(await screen.findByText("Turnos en total")).toBeInTheDocument();
    });

    it.each(["MEDICO", "PACIENTE"] as const)("un %s es mandado al inicio y no se piden las métricas", (rol) => {
      abrir("/metricas", rol);

      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
      expect(metricasApi.obtenerMetricas).not.toHaveBeenCalled();
    });
  });

  describe("verificar pacientes y auditoría: solo admin", () => {
    it("el admin entra a verificar pacientes y se piden los pendientes", async () => {
      abrir("/pacientes/pendientes", "ADMIN");

      expect(await screen.findByText("No hay pacientes pendientes de verificación.")).toBeInTheDocument();
      expect(pacientesApi.pacientesPendientes).toHaveBeenCalled();
    });

    it("el admin entra a la auditoría y se pide el registro de accesos", async () => {
      abrir("/auditoria", "ADMIN");

      expect(await screen.findByText("Todavía no se registró ningún acceso.")).toBeInTheDocument();
      expect(auditoriaApi.accesosHistorial).toHaveBeenCalled();
    });

    it.each([["/pacientes/pendientes"], ["/auditoria"]])("%s: un médico y un paciente son mandados al inicio sin pedir nada", (ruta) => {
      abrir(ruta, "MEDICO");
      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
      cleanup();

      abrir(ruta, "PACIENTE");
      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
      expect(pacientesApi.pacientesPendientes).not.toHaveBeenCalled();
      expect(auditoriaApi.accesosHistorial).not.toHaveBeenCalled();
    });

    it.each([["/pacientes/pendientes"], ["/auditoria"]])("%s: sin sesión manda a iniciar sesión", (ruta) => {
      abrir(ruta);

      expect(screen.getByRole("heading", { name: "Iniciar sesión" })).toBeInTheDocument();
    });
  });

  describe("usuarios: solo admin", () => {
    it("el admin entra y se pide la lista de cuentas", async () => {
      abrir("/usuarios", "ADMIN");

      expect(await screen.findByRole("heading", { name: "Usuarios", level: 1 })).toBeInTheDocument();
      expect(usuariosApi.listarUsuarios).toHaveBeenCalled();
    });

    it.each(["RECEPCIONISTA", "MEDICO", "PACIENTE"] as const)("un %s es mandado al inicio sin pedir nada", (rol) => {
      abrir("/usuarios", rol);

      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
      expect(usuariosApi.listarUsuarios).not.toHaveBeenCalled();
    });
  });

  describe("el mostrador (recepción y admin): agenda, verificar pacientes y feriados", () => {
    it.each(["RECEPCIONISTA", "ADMIN"] as const)("un %s entra a feriados y ausencias y se piden los períodos", async (rol) => {
      abrir("/dias-no-laborables", rol);

      expect(await screen.findByRole("heading", { name: "Feriados y ausencias" })).toBeInTheDocument();
      expect(diasApi.listarDiasNoLaborables).toHaveBeenCalled();
    });

    it("la recepción entra a la agenda y a verificar pacientes", async () => {
      abrir("/agenda", "RECEPCIONISTA");
      expect(await screen.findByText("Elegí un médico para ver su agenda.")).toBeInTheDocument();
      cleanup();

      abrir("/pacientes/pendientes", "RECEPCIONISTA");
      expect(await screen.findByText("No hay pacientes pendientes de verificación.")).toBeInTheDocument();
    });

    it.each(["MEDICO", "PACIENTE"] as const)("un %s es mandado al inicio de feriados y ausencias sin pedir nada", (rol) => {
      abrir("/dias-no-laborables", rol);

      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
      expect(diasApi.listarDiasNoLaborables).not.toHaveBeenCalled();
    });

    it.each([["/metricas"], ["/auditoria"], ["/pacientes/3/historial"], ["/turnos/1/factura"]])(
      "la recepción no entra a %s (métricas, auditoría, historias clínicas, facturas)",
      (ruta) => {
        abrir(ruta, "RECEPCIONISTA");

        expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
        expect(metricasApi.obtenerMetricas).not.toHaveBeenCalled();
        expect(auditoriaApi.accesosHistorial).not.toHaveBeenCalled();
        expect(historialApi.historialDePaciente).not.toHaveBeenCalled();
      },
    );
  });

  describe("historial de un paciente: solo médico y admin", () => {
    it.each(["MEDICO", "ADMIN"] as const)("un %s entra y se pide el historial de ese paciente", async (rol) => {
      abrir("/pacientes/3/historial", rol);

      expect(await screen.findByText("Este paciente todavía no tiene consultas atendidas.")).toBeInTheDocument();
      expect(historialApi.historialDePaciente).toHaveBeenCalledWith(3);
    });

    it("un paciente es mandado al inicio y no se pide el historial de nadie", () => {
      abrir("/pacientes/3/historial", "PACIENTE");

      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
      expect(historialApi.historialDePaciente).not.toHaveBeenCalled();
    });
  });

  describe("pantallas de paciente: solo paciente", () => {
    it.each(["/mis-turnos", "/historial", "/mi-cobertura"])("%s: un paciente entra", (ruta) => {
      abrir(ruta, "PACIENTE");

      expect(screen.queryByRole("heading", { name: TITULO_INICIO })).not.toBeInTheDocument();
    });

    it.each(["/mis-turnos", "/historial", "/mi-cobertura"])("%s: un médico es mandado al inicio", (ruta) => {
      abrir(ruta, "MEDICO");

      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
    });
  });

  describe("agenda y facturación: solo médico y admin", () => {
    it.each(["/agenda", "/turnos/1/factura"])("%s: un paciente es mandado al inicio", (ruta) => {
      abrir(ruta, "PACIENTE");

      expect(screen.getByRole("heading", { name: TITULO_INICIO })).toBeInTheDocument();
    });

    it("un médico entra a su agenda", async () => {
      abrir("/agenda", "MEDICO");

      expect(await screen.findByRole("heading", { name: "Agenda" })).toBeInTheDocument();
    });
  });
});
