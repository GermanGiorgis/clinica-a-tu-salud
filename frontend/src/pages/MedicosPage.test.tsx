import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as medicosApi from "../api/medicos";
import * as patologiasApi from "../api/patologias";
import { renderConSesion } from "../test/render";
import type { Medico, Patologia } from "../types";
import { MedicosPage } from "./MedicosPage";

vi.mock("../api/medicos");
vi.mock("../api/patologias");

const clinica = { id: 1, nombre: "Clínica Médica", descripcion: null };
const dermato = { id: 2, nombre: "Dermatología", descripcion: null };

const laura: Medico = { id: 5, nombre: "Laura", apellido: "Lopez", matricula: "MP-5", especialidades: [clinica] };
const pedro: Medico = { id: 7, nombre: "Pedro", apellido: "Prueba", matricula: "MP-7", especialidades: [dermato] };
const sinEspecialidad: Medico = { id: 9, nombre: "Nuevo", apellido: "Ingreso", matricula: "MP-9", especialidades: [] };

const patologias: Patologia[] = [
  { id: 1, nombre: "Granito en la piel", especialidad: dermato },
  { id: 2, nombre: "Fiebre persistente", especialidad: clinica },
];

describe("MedicosPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(patologiasApi.listarPatologias).mockResolvedValue(patologias);
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([laura, pedro]);
  });

  it("lista los médicos con matrícula y especialidad, cada uno enlazado a su ficha", async () => {
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    const enlace = await screen.findByRole("link", { name: /Pedro Prueba/ });
    expect(enlace).toHaveAttribute("href", "/medicos/7");
    expect(screen.getByText("Matrícula MP-7")).toBeInTheDocument();
    expect(screen.getByText("Dermatología")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Laura Lopez/ })).toHaveAttribute("href", "/medicos/5");
  });

  it("un médico sin especialidad cargada lo dice", async () => {
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([sinEspecialidad]);
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    expect(await screen.findByText("Sin especialidad cargada")).toBeInTheDocument();
  });

  it("al principio no filtra por especialidad", async () => {
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    await screen.findByRole("link", { name: /Pedro Prueba/ });

    expect(medicosApi.listarMedicos).toHaveBeenLastCalledWith(undefined);
  });

  it("elegir una patología filtra los médicos por la especialidad que le corresponde", async () => {
    vi.mocked(medicosApi.listarMedicos).mockImplementation(async (especialidadId) =>
      especialidadId === 2 ? [pedro] : [laura, pedro],
    );
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    await userEvent.selectOptions(await screen.findByLabelText(/¿Qué te pasa\?/), "Granito en la piel");

    await waitFor(() => expect(medicosApi.listarMedicos).toHaveBeenLastCalledWith(2));
    await waitFor(() => expect(screen.queryByRole("link", { name: /Laura Lopez/ })).not.toBeInTheDocument());
    expect(screen.getByRole("link", { name: /Pedro Prueba/ })).toBeInTheDocument();
  });

  it("volver a Ver todos quita el filtro", async () => {
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    const selector = await screen.findByLabelText(/¿Qué te pasa\?/);
    await userEvent.selectOptions(selector, "Fiebre persistente");
    await waitFor(() => expect(medicosApi.listarMedicos).toHaveBeenLastCalledWith(1));
    await userEvent.selectOptions(selector, "Ver todos los médicos");

    await waitFor(() => expect(medicosApi.listarMedicos).toHaveBeenLastCalledWith(undefined));
  });

  it("si no hay médicos para esa especialidad lo explica", async () => {
    vi.mocked(medicosApi.listarMedicos).mockResolvedValue([]);
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    expect(await screen.findByText("No hay médicos para esa especialidad.")).toBeInTheDocument();
  });

  it("si todavía no hay patologías cargadas no muestra el selector de síntomas", async () => {
    vi.mocked(patologiasApi.listarPatologias).mockResolvedValue([]);
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    await screen.findByRole("link", { name: /Pedro Prueba/ });

    expect(screen.queryByLabelText(/¿Qué te pasa\?/)).not.toBeInTheDocument();
  });

  it("si falla la carga de médicos muestra un error", async () => {
    vi.mocked(medicosApi.listarMedicos).mockRejectedValue(new Error("caído"));
    renderConSesion(<MedicosPage />, { ruta: "/medicos", path: "/medicos" });

    expect(await screen.findByText("No se pudo cargar la lista de médicos.")).toBeInTheDocument();
  });
});
