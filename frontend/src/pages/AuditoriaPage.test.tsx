import { screen, within } from "@testing-library/react";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as auditoriaApi from "../api/auditoria";
import { usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import type { AccesoHistorial } from "../types";
import { AuditoriaPage } from "./AuditoriaPage";

vi.mock("../api/auditoria");

function acceso(extra: Partial<AccesoHistorial> = {}): AccesoHistorial {
  return {
    id: 1, usuarioId: 11, usuarioEmail: "dra@clinica.test", usuarioNombre: "Laura Lopez", usuarioRol: "MEDICO",
    pacienteId: 3, pacienteNombre: "Pablo Paz", accedidoEn: "2026-10-05T12:30:15", ...extra,
  };
}

function montar() {
  renderConSesion(<AuditoriaPage />, { usuario: usuario("ADMIN"), ruta: "/auditoria", path: "/auditoria" });
}

describe("AuditoriaPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("mientras carga muestra el estado de carga", () => {
    vi.mocked(auditoriaApi.accesosHistorial).mockReturnValue(new Promise(() => {}));
    montar();

    expect(screen.getByText("Cargando…")).toBeInTheDocument();
  });

  it("sin accesos registrados lo dice en lugar de mostrar una tabla vacía", async () => {
    vi.mocked(auditoriaApi.accesosHistorial).mockResolvedValue([]);
    montar();

    expect(await screen.findByText("Todavía no se registró ningún acceso.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("lista quién accedió (nombre, email y rol), a qué historial y cuándo", async () => {
    vi.mocked(auditoriaApi.accesosHistorial).mockResolvedValue([
      acceso(),
      acceso({ id: 2, usuarioEmail: "admin@clinica.test", usuarioNombre: "Admin Clínica", usuarioRol: "ADMIN", pacienteNombre: "Ana Paz" }),
    ]);
    montar();

    const tabla = await screen.findByRole("table");
    const filas = within(tabla).getAllByRole("row").slice(1);   // sin el encabezado

    expect(filas).toHaveLength(2);
    expect(filas[0]).toHaveTextContent("Laura Lopez");
    expect(filas[0]).toHaveTextContent("dra@clinica.test");
    expect(filas[0]).toHaveTextContent("MEDICO");
    expect(filas[0]).toHaveTextContent("Pablo Paz");
    expect(filas[0]).toHaveTextContent("12:30:15");
    expect(filas[1]).toHaveTextContent("ADMIN");
    expect(filas[1]).toHaveTextContent("Ana Paz");
  });

  it("si la API falla muestra su mensaje", async () => {
    const respuesta = { status: 403, data: { error: "No tenés permiso para hacer esto" } } as AxiosResponse;
    vi.mocked(auditoriaApi.accesosHistorial).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    montar();

    expect(await screen.findByText("No tenés permiso para hacer esto")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});
