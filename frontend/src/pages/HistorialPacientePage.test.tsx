import { screen } from "@testing-library/react";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as historialApi from "../api/historial";
import { entradaHistorial, usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import { HistorialPacientePage } from "./HistorialPacientePage";
import { HistorialPage } from "./HistorialPage";

vi.mock("../api/historial");

function montarDePaciente(estado?: { pacienteNombre: string }) {
  renderConSesion(<HistorialPacientePage />, {
    usuario: usuario("MEDICO"),
    ruta: { pathname: "/pacientes/3/historial", state: estado },
    path: "/pacientes/:pacienteId/historial",
  });
}

describe("HistorialPacientePage (médico/admin viendo a un paciente)", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("pide el historial del paciente de la URL", async () => {
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([]);
    montarDePaciente();

    await screen.findByText("Este paciente todavía no tiene consultas atendidas.");

    expect(historialApi.historialDePaciente).toHaveBeenCalledWith(3);
  });

  it("titula con el nombre del paciente cuando viene de la agenda", async () => {
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([]);
    montarDePaciente({ pacienteNombre: "Pablo Paciente" });

    expect(await screen.findByRole("heading", { name: "Historial de Pablo Paciente" })).toBeInTheDocument();
  });

  it("si se entra directo por la URL usa un título genérico", async () => {
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([]);
    montarDePaciente();

    expect(await screen.findByRole("heading", { name: "Historial del paciente" })).toBeInTheDocument();
  });

  it("muestra diagnósticos, recetas y estudios (con resultado o pendientes) de cada consulta", async () => {
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([entradaHistorial()]);
    montarDePaciente();

    expect(await screen.findByText("Dolor de cabeza leve desde hace 2 días.")).toBeInTheDocument();
    expect(screen.getByText(/Cefalea tensional/)).toBeInTheDocument();
    expect(screen.getByText("(G44.2)")).toBeInTheDocument();
    expect(screen.getByText("Tomar con las comidas")).toBeInTheDocument();
    expect(screen.getByText(/Ibuprofeno — 1 comprimido, cada 8 horas, por 5 días/)).toBeInTheDocument();
    expect(screen.getByText(/Hemograma completo: Todo normal/)).toBeInTheDocument();
    expect(screen.getByText(/Radiografía de tórax \(pendiente\)/)).toBeInTheDocument();
  });

  it("si la consulta no tiene diagnósticos, recetas ni estudios no muestra esas secciones", async () => {
    const vacia = entradaHistorial();
    vacia.consulta = { ...vacia.consulta, diagnosticos: [], recetas: [], ordenesEstudio: [] };
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([vacia]);
    montarDePaciente();

    await screen.findByText("Dolor de cabeza leve desde hace 2 días.");

    expect(screen.queryByRole("heading", { name: "Diagnósticos" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Recetas" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Estudios" })).not.toBeInTheDocument();
  });

  it("si la API lo prohíbe (médico que nunca atendió a ese paciente) muestra su mensaje", async () => {
    const respuesta = { status: 403, data: { error: "Nunca tuviste un turno con ese paciente" } } as AxiosResponse;
    vi.mocked(historialApi.historialDePaciente).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    montarDePaciente();

    expect(await screen.findByText("Nunca tuviste un turno con ese paciente")).toBeInTheDocument();
    expect(screen.queryByText("Este paciente todavía no tiene consultas atendidas.")).not.toBeInTheDocument();
  });

  it("ofrece volver a la agenda", async () => {
    vi.mocked(historialApi.historialDePaciente).mockResolvedValue([]);
    montarDePaciente();

    expect(await screen.findByRole("link", { name: /Volver a la agenda/ })).toHaveAttribute("href", "/agenda");
  });
});

describe("HistorialPage (el paciente viendo el suyo)", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  function montarPropio() {
    renderConSesion(<HistorialPage />, { usuario: usuario("PACIENTE"), ruta: "/historial", path: "/historial" });
  }

  it("muestra el historial propio", async () => {
    vi.mocked(historialApi.miHistorial).mockResolvedValue([entradaHistorial()]);
    montarPropio();

    expect(await screen.findByText(/Cefalea tensional/)).toBeInTheDocument();
    expect(historialApi.historialDePaciente).not.toHaveBeenCalled();
  });

  it("sin consultas atendidas lo explica", async () => {
    vi.mocked(historialApi.miHistorial).mockResolvedValue([]);
    montarPropio();

    expect(await screen.findByText("Todavía no tenés consultas atendidas.")).toBeInTheDocument();
  });

  it("si falla la carga muestra un error", async () => {
    vi.mocked(historialApi.miHistorial).mockRejectedValue(new Error("caído"));
    montarPropio();

    expect(await screen.findByText("No se pudo cargar tu historial.")).toBeInTheDocument();
  });
});
