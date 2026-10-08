import { AxiosError, type AxiosResponse } from "axios";
import { describe, expect, it } from "vitest";
import { extraerError } from "./errores";

function errorHttp(status: number, data: unknown): AxiosError {
  return new AxiosError("fallo", "ERR_BAD_REQUEST", undefined, undefined, { status, data } as AxiosResponse);
}

describe("extraerError", () => {
  it("devuelve el mensaje que manda la API en { error }", () => {
    expect(extraerError(errorHttp(409, { error: "Ese horario ya no está disponible" }))).toBe(
      "Ese horario ya no está disponible",
    );
  });

  it("agrega el detalle por campo cuando la API devuelve errores de validación", () => {
    const err = errorHttp(400, { error: "Datos inválidos", errores: { nombre: "no puede estar vacío", dni: "es obligatorio" } });
    expect(extraerError(err)).toBe("Datos inválidos (Nombre: no puede estar vacío, DNI: es obligatorio)");
  });

  it("nombra los campos como los ve la persona y no como los llama la API", () => {
    const err = errorHttp(400, {
      error: "Revisá los datos ingresados",
      errores: { password: "demasiado corta", fechaNacimiento: "debe ser una fecha pasada", pacienteDni: "inválido" },
    });
    expect(extraerError(err)).toBe(
      "Revisá los datos ingresados (Contraseña: demasiado corta, Fecha de nacimiento: debe ser una fecha pasada, DNI del paciente: inválido)",
    );
  });

  it("si el campo no está en la lista deja el nombre tal cual en vez de perder el dato", () => {
    const err = errorHttp(400, { error: "Datos inválidos", errores: { campoNuevo: "no va" } });
    expect(extraerError(err)).toBe("Datos inválidos (campoNuevo: no va)");
  });

  it("usa el mensaje por defecto si la respuesta no trae { error }", () => {
    expect(extraerError(errorHttp(500, {}))).toBe("Ocurrió un error. Probá de nuevo.");
  });

  it("usa el mensaje por defecto si no hubo respuesta (servidor caído)", () => {
    expect(extraerError(new AxiosError("Network Error"))).toBe("Ocurrió un error. Probá de nuevo.");
  });

  it("usa el mensaje por defecto con un error que no es de axios", () => {
    expect(extraerError(new Error("boom"))).toBe("Ocurrió un error. Probá de nuevo.");
    expect(extraerError("texto suelto")).toBe("Ocurrió un error. Probá de nuevo.");
  });

  it("respeta un mensaje por defecto personalizado", () => {
    expect(extraerError(new Error("boom"), "No se pudo cargar")).toBe("No se pudo cargar");
  });
});
