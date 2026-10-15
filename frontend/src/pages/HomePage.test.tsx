import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as especialidadesApi from "../api/especialidades";
import * as patologiasApi from "../api/patologias";
import { usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import type { Especialidad, Patologia } from "../types";
import { HomePage } from "./HomePage";

vi.mock("../api/especialidades");
vi.mock("../api/patologias");

const catalogo: Especialidad[] = [
  { id: 1, nombre: "Clínica Médica", descripcion: null },
  { id: 2, nombre: "Pediatría", descripcion: null },
  { id: 3, nombre: "Dermatología", descripcion: null },
  { id: 4, nombre: "Traumatología y Ortopedia", descripcion: null },
  { id: 5, nombre: "Geriatría", descripcion: null },
];

const sintomas: Patologia[] = [
  { id: 10, nombre: "Dolor de cabeza", especialidades: [catalogo[0]] },
  { id: 11, nombre: "Lunares o manchas en la piel", especialidades: [catalogo[2]] },
  { id: 12, nombre: "Embarazo o control del embarazo", especialidades: [catalogo[1]] },
];

function montar(sesion: Parameters<typeof renderConSesion>[1] = {}) {
  renderConSesion(<HomePage />, { ruta: "/", path: "/", ...sesion });
}

describe("HomePage (portada)", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(especialidadesApi.listarEspecialidades).mockResolvedValue(catalogo);
    vi.mocked(patologiasApi.listarPatologias).mockResolvedValue(sintomas);
  });

  it("presenta la clínica por su nombre y la acción principal es sacar un turno", async () => {
    montar();

    expect(screen.getByRole("heading", { name: "Pedí tu turno en minutos", level: 1 })).toBeInTheDocument();
    expect(screen.getAllByText("Clínica a tu salud").length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: "Sacar un turno" })).toHaveAttribute("href", "/medicos");
  });

  it("no es un manual: no explica los roles ni muestra las fases del proyecto", async () => {
    montar();
    await screen.findByRole("heading", { name: "Especialidades" });

    for (const rol of ["Paciente", "Médico", "Recepción", "Admin", "Roles", "Fases del proyecto"]) {
      expect(screen.queryByRole("heading", { name: rol })).not.toBeInTheDocument();
    }
    expect(screen.queryByText(/Todas las fases hechas/)).not.toBeInTheDocument();
    expect(screen.queryByText(/administrador coordina/)).not.toBeInTheDocument();
  });

  it("a quien no tiene sesión le ofrece crear una cuenta o entrar", async () => {
    montar();

    expect(screen.getByRole("link", { name: "Crear cuenta de paciente" })).toHaveAttribute("href", "/registro");
    expect(screen.getByRole("heading", { name: "¿Primera vez?" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ya tengo cuenta" })).toHaveAttribute("href", "/login");
  });

  it("a un paciente con sesión no le ofrece crear otra cuenta y le deja ir a sus turnos", async () => {
    montar({ usuario: usuario("PACIENTE") });

    expect(screen.getByRole("link", { name: "Mis turnos" })).toHaveAttribute("href", "/mis-turnos");
    expect(screen.getByRole("link", { name: "Sacar un turno" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Crear cuenta/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "¿Primera vez?" })).not.toBeInTheDocument();
  });

  it.each(["MEDICO", "RECEPCIONISTA", "ADMIN"] as const)("a %s lo lleva a la agenda en lugar de sacar un turno", async (rol) => {
    montar({ usuario: usuario(rol) });

    expect(screen.getByRole("link", { name: "Ir a la agenda" })).toHaveAttribute("href", "/agenda");
    expect(screen.queryByRole("link", { name: "Sacar un turno" })).not.toBeInTheDocument();
  });

  it("muestra las especialidades destacadas que existen en el catálogo, cada una enlazada a sus médicos", async () => {
    montar();

    expect(await screen.findByRole("link", { name: "Dermatología" })).toHaveAttribute("href", "/medicos/especialidad/3");
    expect(screen.getByRole("link", { name: "Clínica Médica" })).toHaveAttribute("href", "/medicos/especialidad/1");
    expect(screen.getByRole("link", { name: "Traumatología y Ortopedia" })).toHaveAttribute("href", "/medicos/especialidad/4");
    // no destacadas, o que el catálogo no tiene: no aparecen ni se inventan
    expect(screen.queryByRole("link", { name: "Geriatría" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Cardiología/ })).not.toBeInTheDocument();
  });

  it("dice cuántas especialidades tiene la clínica y lleva a verlas todas", async () => {
    montar();

    expect(await screen.findByRole("link", { name: /Ver las 5 especialidades/ })).toHaveAttribute("href", "/medicos");
  });

  it("deja elegir un síntoma frecuente: lleva a las especialidades filtradas por ese síntoma", async () => {
    montar();

    expect(await screen.findByRole("link", { name: "Dolor de cabeza" })).toHaveAttribute("href", "/medicos?sintoma=10");
    expect(screen.getByRole("link", { name: "Lunares o manchas en la piel" })).toHaveAttribute("href", "/medicos?sintoma=11");
    expect(screen.queryByRole("link", { name: "Embarazo o control del embarazo" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Ver todos los síntomas/ })).toHaveAttribute("href", "/medicos");
  });

  it("si los catálogos no cargan, la portada abre igual con lo esencial", async () => {
    vi.mocked(especialidadesApi.listarEspecialidades).mockRejectedValue(new Error("caído"));
    vi.mocked(patologiasApi.listarPatologias).mockRejectedValue(new Error("caído"));
    montar();

    await waitFor(() => expect(especialidadesApi.listarEspecialidades).toHaveBeenCalled());
    expect(screen.getByRole("heading", { name: "Pedí tu turno en minutos" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sacar un turno" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Especialidades" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "¿Qué te pasa?" })).not.toBeInTheDocument();
  });

  it("explica por qué conviene, sin jerga técnica", () => {
    montar();

    expect(screen.getByRole("heading", { name: "Una clínica pensada para vos" })).toBeInTheDocument();
    for (const titulo of ["Reservá cuando quieras", "Todas las especialidades", "Tu historial a mano", "Tus datos, protegidos"]) {
      expect(screen.getByRole("heading", { name: titulo, level: 3 })).toBeInTheDocument();
    }
  });

  it("avisa qué hacer ante una urgencia", () => {
    montar();

    expect(screen.getByText(/llamá al 107 o andá a una guardia/)).toBeInTheDocument();
  });
});
