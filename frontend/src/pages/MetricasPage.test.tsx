import { screen, within } from "@testing-library/react";
import { AxiosError, type AxiosResponse } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as metricasApi from "../api/metricas";
import { metricas, usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import { MetricasPage } from "./MetricasPage";

vi.mock("../api/metricas");

// Intl separa el símbolo de la moneda con un espacio no cortable; se normaliza para comparar.
const sinEspaciosRaros = (texto: string | null) => (texto ?? "").replace(/\s/g, " ");

function montar() {
  renderConSesion(<MetricasPage />, { usuario: usuario("ADMIN"), ruta: "/metricas", path: "/metricas" });
}

/** La tarjeta de indicador cuya etiqueta coincide (la misma palabra puede aparecer también en la tabla). */
function tarjeta(etiqueta: RegExp) {
  const tarjetas = Array.from(document.querySelectorAll<HTMLElement>(".metricas__kpi"));
  const encontrada = tarjetas.find((t) => etiqueta.test(t.querySelector("span")?.textContent ?? ""));
  if (!encontrada) {
    throw new Error(`No hay una tarjeta con la etiqueta ${etiqueta}`);
  }
  return encontrada;
}

describe("MetricasPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("mientras carga muestra el estado de carga", () => {
    vi.mocked(metricasApi.obtenerMetricas).mockReturnValue(new Promise(() => {}));
    montar();

    expect(screen.getByText("Cargando…")).toBeInTheDocument();
  });

  it("si la API falla muestra el error y ninguna cifra", async () => {
    const respuesta = { status: 403, data: { error: "No tenés permiso para hacer esto" } } as AxiosResponse;
    vi.mocked(metricasApi.obtenerMetricas).mockRejectedValue(new AxiosError("x", "ERR_BAD_REQUEST", undefined, undefined, respuesta));
    montar();

    expect(await screen.findByText("No tenés permiso para hacer esto")).toBeInTheDocument();
    expect(screen.queryByText("Turnos en total")).not.toBeInTheDocument();
  });

  it("muestra los indicadores generales", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas());
    montar();

    await screen.findByText("Turnos en total");

    expect(within(tarjeta(/Turnos en total/)).getByText("6")).toBeInTheDocument();
    expect(within(tarjeta(/Pacientes atendidos/)).getByText("1")).toBeInTheDocument();
    expect(within(tarjeta(/Ausentismo/)).getByText("33,33%")).toBeInTheDocument();
    expect(within(tarjeta(/Satisfacción \(/)).getByText("4")).toBeInTheDocument();
    expect(sinEspaciosRaros(within(tarjeta(/Facturado/)).getByText(/35\.500/).textContent)).toBe("$ 35.500,50");
  });

  it("escribe encuesta en singular o plural según la cantidad", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas({ totalEncuestas: 1 }));
    montar();

    expect(await screen.findByText("Satisfacción (1 encuesta)")).toBeInTheDocument();
  });

  it("sin datos para calcular el ausentismo y la satisfacción muestra un guion y no un número inventado", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(
      metricas({ tasaAusentismo: null, puntajePromedio: null, totalEncuestas: 0 }),
    );
    montar();

    await screen.findByText("Turnos en total");

    expect(within(tarjeta(/Ausentismo/)).getByText("—")).toBeInTheDocument();
    expect(within(tarjeta(/Satisfacción/)).getByText("—")).toBeInTheDocument();
  });

  it("muestra los turnos por estado con su etiqueta y cantidad, incluso los que están en cero", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas());
    montar();

    const panel = (await screen.findByRole("heading", { name: "Turnos por estado" })).closest("section") as HTMLElement;

    for (const [estado, cantidad] of [["Reservado", "1"], ["Confirmado", "0"], ["Atendido", "2"], ["Ausente", "1"], ["Cancelado", "2"]]) {
      const fila = within(panel).getByText(estado).closest("li") as HTMLElement;
      expect(within(fila).getByText(cantidad)).toBeInTheDocument();
    }
  });

  it("muestra la facturación por medio de pago", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas());
    montar();

    const panel = (await screen.findByRole("heading", { name: "Facturación por medio de pago" })).closest("section") as HTMLElement;

    const efectivo = within(panel).getByText("Efectivo").closest("li") as HTMLElement;
    expect(sinEspaciosRaros(efectivo.textContent)).toContain("$ 12.000,00");
    const obraSocial = within(panel).getByText("Obra social").closest("li") as HTMLElement;
    expect(sinEspaciosRaros(obraSocial.textContent)).toContain("$ 23.500,50");
    const tarjetaFila = within(panel).getByText("Tarjeta").closest("li") as HTMLElement;
    expect(sinEspaciosRaros(tarjetaFila.textContent)).toContain("$ 0,00");
  });

  it("la barra de cada estado es proporcional al mayor", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas());
    montar();

    const panel = (await screen.findByRole("heading", { name: "Turnos por estado" })).closest("section") as HTMLElement;
    const ancho = (estado: string) =>
      (within(panel).getByText(estado).closest("li")!.querySelector(".metricas__barra") as HTMLElement).style.width;

    expect(ancho("Atendido")).toBe("100%");
    expect(ancho("Reservado")).toBe("50%");
    expect(ancho("Confirmado")).toBe("0%");
  });

  it("lista a cada médico con sus números, y un guion si todavía no tiene encuestas", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas());
    montar();

    const tabla = await screen.findByRole("table");
    const pedro = within(tabla).getByRole("rowheader", { name: "Pedro Prueba" }).closest("tr") as HTMLElement;
    const laura = within(tabla).getByRole("rowheader", { name: "Laura Lopez" }).closest("tr") as HTMLElement;

    expect(within(pedro).getAllByRole("cell").map((c) => sinEspaciosRaros(c.textContent))).toEqual(["2", "1", "4 (2)", "$ 35.500,50"]);
    expect(within(laura).getAllByRole("cell").map((c) => sinEspaciosRaros(c.textContent))).toEqual(["0", "0", "—", "$ 0,00"]);
  });

  it("si no hay médicos cargados lo dice en lugar de mostrar una tabla vacía", async () => {
    vi.mocked(metricasApi.obtenerMetricas).mockResolvedValue(metricas({ medicos: [] }));
    montar();

    expect(await screen.findByText("Todavía no hay médicos cargados.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});
