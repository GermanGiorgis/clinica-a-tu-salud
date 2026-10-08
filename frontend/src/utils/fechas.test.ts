import { afterEach, describe, expect, it, vi } from "vitest";
import { fechaHoraLegible, fechaLegible, hoyISO, ultimoDiaReservableISO, VENTANA_RESERVA_DIAS } from "./fechas";
import { soloDigitos } from "./dni";

describe("fechas", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("hoyISO y el último día reservable salen en formato de input de fecha, a 60 días de distancia", () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2030-03-04T10:00:00"));

    expect(hoyISO()).toBe("2030-03-04");
    expect(VENTANA_RESERVA_DIAS).toBe(60);
    expect(ultimoDiaReservableISO()).toBe("2030-05-03");
  });

  it("fechaLegible no corre el día por la zona horaria", () => {
    expect(fechaLegible("2026-10-12")).toBe("lunes, 12 de octubre");
    expect(fechaLegible("2030-01-01")).toContain("1 de enero");
  });

  it("fechaHoraLegible muestra día y hora del turno", () => {
    const texto = fechaHoraLegible("2026-10-12T10:30:00");

    expect(texto).toContain("12 de octubre de 2026");
    expect(texto).toMatch(/10:30/);
  });
});

describe("soloDigitos", () => {
  it("deja solo los números, aunque peguen el DNI con puntos o espacios", () => {
    expect(soloDigitos("30.111.222")).toBe("30111222");
    expect(soloDigitos(" 30 111 222 ")).toBe("30111222");
    expect(soloDigitos("ABC-123")).toBe("123");
  });

  it("corta en 9 dígitos y acepta vacío", () => {
    expect(soloDigitos("1234567890123")).toBe("123456789");
    expect(soloDigitos("")).toBe("");
  });
});
