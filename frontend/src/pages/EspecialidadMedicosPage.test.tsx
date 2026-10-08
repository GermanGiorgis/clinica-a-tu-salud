import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as especialidadesApi from "../api/especialidades";
import * as medicosApi from "../api/medicos";
import { renderConSesion } from "../test/render";
import type { Especialidad, Medico } from "../types";
import { EspecialidadMedicosPage } from "./EspecialidadMedicosPage";

vi.mock("../api/especialidades");
vi.mock("../api/medicos");

const dermato: Especialidad = { id: 2, nombre: "Dermatología", descripcion: "Piel, pelo y uñas" };
const clinica: Especialidad = { id: 1, nombre: "Clínica Médica", descripcion: null };

const pedro: Medico = { id: 7, nombre: "Pedro", apellido: "Prueba", matricula: "MP-7", especialidades: [dermato] };
const ana: Medico = { id: 8, nombre: "Ana", apellido: "Alvarez", matricula: "MP-8", especialidades: [dermato, clinica] };

function montar(ruta = "/medicos/especialidad/2") {
  renderConSesion(<EspecialidadMedicosPage />, { ruta, path: "/medicos/especialidad/:especialidadId" });
}

describe("EspecialidadMedicosPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(especialidadesApi.listarEspecialidades).mockResolvedValue([clinica, dermato]);
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([pedro, ana]);
  });

  it("muestra el nombre de la especialidad y los médicos que la atienden, cada uno enlazado a su ficha", async () => {
    montar();

    expect(await screen.findByRole("heading", { name: "Dermatología", level: 1 })).toBeInTheDocument();
    expect(screen.getByText("Piel, pelo y uñas")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /Pedro Prueba/ })).toHaveAttribute("href", "/medicos/7");
    expect(screen.getByRole("link", { name: /Ana Alvarez/ })).toHaveAttribute("href", "/medicos/8");
    expect(screen.getByText("Matrícula MP-7")).toBeInTheDocument();
  });

  it("pide solo los médicos de esa especialidad", async () => {
    montar("/medicos/especialidad/2");

    await screen.findByRole("link", { name: /Pedro Prueba/ });

    expect(medicosApi.listarMedicos).toHaveBeenCalledWith(2);
  });

  it("un médico con varias especialidades muestra todas; con una sola no repite la que ya se está viendo", async () => {
    montar();

    await screen.findByRole("link", { name: /Ana Alvarez/ });

    expect(screen.getByText("Dermatología, Clínica Médica")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Pedro Prueba/ })).not.toHaveTextContent("Dermatología");
  });

  it("tiene un enlace para volver a todas las especialidades", async () => {
    montar();

    expect(await screen.findByRole("link", { name: /Todas las especialidades/ })).toHaveAttribute("href", "/medicos");
  });

  it("si no hay médicos en esa especialidad lo explica", async () => {
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([]);
    montar();

    expect(await screen.findByText("Todavía no hay médicos en esta especialidad.")).toBeInTheDocument();
  });

  it("si la especialidad no existe lo dice y no lista médicos", async () => {
    montar("/medicos/especialidad/99");

    expect(await screen.findByText("No existe esa especialidad.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Pedro Prueba/ })).not.toBeInTheDocument();
  });

  it("si falla la carga de médicos muestra un error", async () => {
    vi.mocked(medicosApi.listarMedicos).mockRejectedValue(new Error("caído"));
    montar();

    expect(await screen.findByText("No se pudo cargar la lista de médicos.")).toBeInTheDocument();
  });
});
