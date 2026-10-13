import { AxiosError, type AxiosResponse } from "axios";
import { describe, expect, it } from "vitest";
import { capitalizar, erroresPorCampo, extraerError } from "./errores";

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

  it("sin conexión con el servidor lo dice, en vez de un error genérico", () => {
    const mensaje = extraerError(new AxiosError("Network Error", "ERR_NETWORK"));
    expect(mensaje).toMatch(/No se pudo conectar con el servidor/);
    expect(mensaje).toMatch(/conexión a internet/);
  });

  it("si el servidor tarda demasiado lo dice", () => {
    expect(extraerError(new AxiosError("timeout", "ECONNABORTED"))).toMatch(/tardó demasiado en responder/);
    expect(extraerError(new AxiosError("timeout", "ETIMEDOUT"))).toMatch(/tardó demasiado en responder/);
  });

  it("un 502/503/504 (el proxy no encuentra al servidor) se explica como falta de conexión con el servidor", () => {
    for (const estado of [502, 503, 504]) {
      expect(extraerError(errorHttp(estado, "Bad Gateway"))).toMatch(/No se pudo conectar con el servidor/);
    }
  });

  it("un 500 sin mensaje dice que el problema es del servidor y el código", () => {
    expect(extraerError(errorHttp(500, {}))).toMatch(/problema al procesar el pedido \(error 500\)/);
    expect(extraerError(errorHttp(500, "<html>"))).toMatch(/\(error 500\)/);
  });

  it("prefiere el mensaje que manda el servidor aunque sea un 500", () => {
    expect(extraerError(errorHttp(500, { error: "Ocurrió un error inesperado. Probá de nuevo en unos minutos" })))
      .toBe("Ocurrió un error inesperado. Probá de nuevo en unos minutos");
  });

  it("con un texto propio de la pantalla, lo encabeza y le suma la causa", () => {
    expect(extraerError(new AxiosError("Network Error", "ERR_NETWORK"), "No se pudo cargar la lista de médicos."))
      .toMatch(/^No se pudo cargar la lista de médicos\. No se pudo conectar con el servidor/);
  });

  it("si la falla no tiene una causa conocida (un 404 sin cuerpo) usa el texto por defecto", () => {
    expect(extraerError(errorHttp(404, {}))).toBe("Ocurrió un error. Probá de nuevo.");
    expect(extraerError(errorHttp(404, {}), "No se pudo cargar")).toBe("No se pudo cargar");
  });

  it("usa el mensaje por defecto con un error que no es de axios", () => {
    expect(extraerError(new Error("boom"))).toBe("Ocurrió un error. Probá de nuevo.");
    expect(extraerError("texto suelto")).toBe("Ocurrió un error. Probá de nuevo.");
  });

  it("respeta un mensaje por defecto personalizado", () => {
    expect(extraerError(new Error("boom"), "No se pudo cargar")).toBe("No se pudo cargar");
  });

  describe("erroresPorCampo", () => {
    it("devuelve los errores por campo de un 400", () => {
      expect(erroresPorCampo(errorHttp(400, { error: "x", errores: { email: "es obligatorio" } }))).toEqual({ email: "es obligatorio" });
    });

    it("devuelve null si no hay detalle por campo", () => {
      expect(erroresPorCampo(errorHttp(409, { error: "Ya existe" }))).toBeNull();
      expect(erroresPorCampo(errorHttp(400, { error: "x", errores: {} }))).toBeNull();
      expect(erroresPorCampo(new Error("boom"))).toBeNull();
      expect(erroresPorCampo(new AxiosError("Network Error"))).toBeNull();
    });
  });

  it("capitaliza el mensaje de campo del servidor", () => {
    expect(capitalizar("es obligatorio")).toBe("Es obligatorio");
    expect(capitalizar("")).toBe("");
  });
});
