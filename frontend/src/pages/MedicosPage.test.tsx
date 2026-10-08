import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as especialidadesApi from "../api/especialidades";
import * as medicosApi from "../api/medicos";
import * as patologiasApi from "../api/patologias";
import { renderConSesion } from "../test/render";
import type { Especialidad, Medico, Patologia } from "../types";
import { MedicosPage } from "./MedicosPage";

vi.mock("../api/especialidades");
vi.mock("../api/medicos");
vi.mock("../api/patologias");

const clinica: Especialidad = { id: 1, nombre: "Clínica Médica", descripcion: "Atención general del adulto" };
const dermato: Especialidad = { id: 2, nombre: "Dermatología", descripcion: "Piel, pelo y uñas" };
const trauma: Especialidad = { id: 3, nombre: "Traumatología y Ortopedia", descripcion: null };

const laura: Medico = { id: 5, nombre: "Laura", apellido: "Lopez", matricula: "MP-5", especialidades: [clinica], activo: true };
const pedro: Medico = { id: 7, nombre: "Pedro", apellido: "Prueba", matricula: "MP-7", especialidades: [dermato], activo: true };
const ana: Medico = { id: 8, nombre: "Ana", apellido: "Alvarez", matricula: "MP-8", especialidades: [dermato, clinica], activo: true };

const patologias: Patologia[] = [{ id: 1, nombre: "Granito en la piel", especialidad: dermato }];

function montar() {
  renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });
}

describe("MedicosPage (especialidades)", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(especialidadesApi.listarEspecialidades).mockResolvedValue([clinica, dermato, trauma]);
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([laura, pedro, ana]);
    vi.mocked(patologiasApi.listarPatologias).mockResolvedValue(patologias);
  });

  it("lista las especialidades, cada una enlazada a la lista de sus médicos", async () => {
    montar();

    const enlace = await screen.findByRole("link", { name: /Dermatología/ });
    expect(enlace).toHaveAttribute("href", "/medicos/especialidad/2");
    expect(screen.getByRole("link", { name: /Clínica Médica/ })).toHaveAttribute("href", "/medicos/especialidad/1");
    expect(screen.getByRole("link", { name: /Traumatología y Ortopedia/ })).toHaveAttribute("href", "/medicos/especialidad/3");
    expect(screen.getByText("Piel, pelo y uñas")).toBeInTheDocument();
  });

  it("las ordena alfabéticamente aunque lleguen en otro orden", async () => {
    vi.mocked(especialidadesApi.listarEspecialidades).mockResolvedValue([trauma, dermato, clinica]);
    montar();

    await screen.findByRole("link", { name: /Dermatología/ });

    const nombres = screen.getAllByRole("link").map((l) => l.textContent ?? "");
    expect(nombres[0]).toMatch(/^Clínica Médica/);
    expect(nombres[1]).toMatch(/^Dermatología/);
    expect(nombres[2]).toMatch(/^Traumatología/);
  });

  it("no muestra los médicos hasta abrir una especialidad", async () => {
    montar();

    await screen.findByRole("link", { name: /Dermatología/ });

    expect(screen.queryByText("Pedro Prueba")).not.toBeInTheDocument();
    expect(screen.queryByText(/Matrícula/)).not.toBeInTheDocument();
  });

  it("dice cuántos médicos tiene cada especialidad, contando a los que tienen más de una", async () => {
    montar();

    const dermatologia = await screen.findByRole("link", { name: /Dermatología/ });
    await waitFor(() => expect(dermatologia).toHaveTextContent("2 médicos"));
    expect(screen.getByRole("link", { name: /Clínica Médica/ })).toHaveTextContent("2 médicos");
    expect(screen.getByRole("link", { name: /Traumatología/ })).toHaveTextContent("Sin médicos por ahora");
  });

  it("usa el singular cuando hay un solo médico", async () => {
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([pedro]);
    montar();

    await waitFor(() => expect(screen.getByRole("link", { name: /Dermatología/ })).toHaveTextContent("1 médico"));
    expect(screen.getByRole("link", { name: /Dermatología/ })).not.toHaveTextContent("1 médicos");
  });

  it("si no se pueden cargar los médicos igual se puede elegir especialidad, sin conteos", async () => {
    vi.mocked(medicosApi.listarMedicos).mockRejectedValue(new Error("caído"));
    montar();

    const enlace = await screen.findByRole("link", { name: /Dermatología/ });
    expect(enlace).not.toHaveTextContent(/médico/);
    expect(screen.queryByText(/No se pudo/)).not.toBeInTheDocument();
  });

  it("el buscador filtra por nombre sin importar tildes ni mayúsculas", async () => {
    montar();
    await screen.findByRole("link", { name: /Dermatología/ });

    await userEvent.type(screen.getByLabelText(/Buscar especialidad/), "TRAUMATOLOGIA");

    expect(screen.getByRole("link", { name: /Traumatología/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Dermatología/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Clínica Médica/ })).not.toBeInTheDocument();
  });

  it("si ninguna especialidad coincide con la búsqueda lo dice", async () => {
    montar();
    await screen.findByRole("link", { name: /Dermatología/ });

    await userEvent.type(screen.getByLabelText(/Buscar especialidad/), "xyz");

    expect(screen.getByText("No encontramos ninguna especialidad con ese nombre.")).toBeInTheDocument();
  });

  it("elegir un síntoma lleva a la especialidad que le corresponde", async () => {
    montar();

    await userEvent.selectOptions(await screen.findByLabelText(/¿Qué te pasa\?/), "Granito en la piel");

    await waitFor(() => expect(screen.getByTestId("ubicacion")).toHaveTextContent("/medicos/especialidad/2"));
  });

  it("si todavía no hay síntomas cargados no muestra ese selector", async () => {
    vi.mocked(patologiasApi.listarPatologias).mockResolvedValue([]);
    montar();

    await screen.findByRole("link", { name: /Dermatología/ });

    expect(screen.queryByLabelText(/¿Qué te pasa\?/)).not.toBeInTheDocument();
  });

  it("sin especialidades cargadas lo explica", async () => {
    vi.mocked(especialidadesApi.listarEspecialidades).mockResolvedValue([]);
    montar();

    expect(await screen.findByText("Todavía no hay especialidades cargadas.")).toBeInTheDocument();
  });

  it("si falla la carga muestra un error", async () => {
    vi.mocked(especialidadesApi.listarEspecialidades).mockRejectedValue(new Error("caído"));
    montar();

    expect(await screen.findByText("No se pudo cargar la lista de especialidades.")).toBeInTheDocument();
  });
});
