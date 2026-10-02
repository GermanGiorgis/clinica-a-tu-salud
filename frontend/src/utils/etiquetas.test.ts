import { describe, expect, it } from "vitest";
import { ETIQUETA_ESTADO, ETIQUETA_MEDIO_PAGO, formatearMoneda } from "./etiquetas";

describe("etiquetas", () => {
  it("hay una etiqueta legible para cada estado de turno", () => {
    expect(Object.keys(ETIQUETA_ESTADO).sort()).toEqual(["ATENDIDO", "AUSENTE", "CANCELADO", "CONFIRMADO", "RESERVADO"]);
    expect(ETIQUETA_ESTADO.CONFIRMADO).toBe("Confirmado");
  });

  it("hay una etiqueta legible para cada medio de pago", () => {
    expect(Object.keys(ETIQUETA_MEDIO_PAGO).sort()).toEqual(["EFECTIVO", "OBRA_SOCIAL", "TARJETA", "TRANSFERENCIA"]);
    expect(ETIQUETA_MEDIO_PAGO.OBRA_SOCIAL).toBe("Obra social");
  });
});

describe("formatearMoneda", () => {
  // Intl separa el símbolo con un espacio no cortable; se normaliza para no depender de eso.
  const normalizar = (texto: string) => texto.replace(/\s/g, " ");

  it("formatea en pesos argentinos con separador de miles y decimales", () => {
    expect(normalizar(formatearMoneda(35500.5))).toBe("$ 35.500,50");
  });

  it("formatea el cero", () => {
    expect(normalizar(formatearMoneda(0))).toBe("$ 0,00");
  });
});
