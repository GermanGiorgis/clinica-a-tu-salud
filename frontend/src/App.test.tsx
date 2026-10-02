import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import * as historialApi from "./api/historial";
import * as metricasApi from "./api/metricas";
import * as turnosApi from "./api/turnos";
import * as medicosApi from "./api/medicos";
import * as motivosApi from "./api/motivosCancelacion";
import * as afiliacionesApi from "./api/afiliaciones";
import * as patologiasApi from "./api/patologias";
import { metricas, usuario } from "./test/fixtures";
import type { Rol } from "./types";

vi.mock("./api/historial");
vi.mock("./api/metricas");
vi.mock("./api/turnos");
vi.mock("./api/medicos");
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
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([]);
    vi.mocked(historialApi.miHistorial).mockResolvedValue([]);
    vi.mocked(turnosApi.misTurnos).mockResolvedValue([]);
    vi.mocked(turnosApi.agendaMedico).mockResolvedValue([]);
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([]);
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

  it("la lista de médicos es pública", async () => {
    abrir("/medicos");

    expect(await screen.findByRole("heading", { name: "Médicos", level: 1 })).toBeInTheDocument();
  });

  describe("sin sesión, las pantallas privadas mandan a iniciar sesión", () => {
    it.each(["/mis-turnos", "/agenda", "/historial", "/mi-cobertura", "/metricas", "/pacientes/3/historial", "/turnos/1/consulta", "/turnos/1/factura"])(
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
