import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { validarCampoRegistro, validarRegistro, type DatosRegistro } from "./validacionRegistro";

const VALIDO: DatosRegistro = {
  nombre: "German", apellido: "Giorgis", email: "germanggiorgis@gmail.com", password: "clave-larga-123",
  telefono: "2613429556", dni: "41191392", fechaNacimiento: "1998-05-22",
};

function con(cambios: Partial<DatosRegistro>): DatosRegistro {
  return { ...VALIDO, ...cambios };
}

describe("validarRegistro", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-13T12:00:00"));
  });
  afterEach(() => vi.useRealTimers());

  it("no marca nada cuando todos los datos están bien", () => {
    expect(validarRegistro(VALIDO)).toEqual({});
  });

  it("el teléfono es opcional", () => {
    expect(validarRegistro(con({ telefono: "" }))).toEqual({});
    expect(validarRegistro(con({ telefono: "   " }))).toEqual({});
  });

  it("marca todo lo que falta junto, para no hacer corregir de a uno", () => {
    const errores = validarRegistro({ nombre: "", apellido: " ", email: "", password: "", telefono: "", dni: "", fechaNacimiento: "" });
    expect(Object.keys(errores)).toEqual(["nombre", "apellido", "email", "password", "dni", "fechaNacimiento"]);
  });

  describe("email", () => {
    it.each([
      ["germanggiorgis.gmail.com", /falta el @/],
      ["@gmail.com", /falta lo que va antes del @/],
      ["germanggiorgis@gmail", /falta la terminación del dominio/],
      ["germanggiorgis@gmail.", /no es válido/],
      ["german giorgis@gmail.com", /no puede tener espacios/],
      ["", /Escribí tu email/],
    ])("%j → explica qué le falta", (email, mensaje) => {
      expect(validarCampoRegistro("email", con({ email }))).toMatch(mensaje);
    });

    it("acepta mayúsculas, puntos, + y subdominios; ignora espacios sobrantes en los bordes", () => {
      for (const email of ["Ana.Paz+turnos@mail.clinica.com.ar", " ana@clinica.test "]) {
        expect(validarCampoRegistro("email", con({ email }))).toBeUndefined();
      }
    });
  });

  describe("contraseña", () => {
    it("dice cuántos caracteres escribió y cuántos necesita", () => {
      expect(validarCampoRegistro("password", con({ password: "corta" }))).toBe("Es muy corta: tiene 5 caracteres y necesita al menos 10.");
    });

    it("acepta 10 caracteres justos y rechaza una de 9", () => {
      expect(validarCampoRegistro("password", con({ password: "1234567890" }))).toBeUndefined();
      expect(validarCampoRegistro("password", con({ password: "123456789" }))).toMatch(/muy corta/);
    });

    it("el límite de 72 se mide en bytes: las tildes y la ñ ocupan 2", () => {
      expect(validarCampoRegistro("password", con({ password: "a".repeat(72) }))).toBeUndefined();
      expect(validarCampoRegistro("password", con({ password: "a".repeat(73) }))).toMatch(/demasiado larga/);
      expect(validarCampoRegistro("password", con({ password: "ñ".repeat(37) }))).toMatch(/demasiado larga/);
    });

    it("pide elegir una si está vacía", () => {
      expect(validarCampoRegistro("password", con({ password: "" }))).toBe("Elegí una contraseña.");
    });
  });

  describe("teléfono", () => {
    it.each(["2613429556", "+54 9 261 342-9556", "(0261) 342-9556", "261 342 9556"])("acepta %j", (telefono) => {
      expect(validarCampoRegistro("telefono", con({ telefono }))).toBeUndefined();
    });

    it("con letras dice que no puede haberlas", () => {
      expect(validarCampoRegistro("telefono", con({ telefono: "261-ABC-1234" }))).toMatch(/Sin letras/);
    });

    it("con muy pocos o demasiados números dice cuántos escribió", () => {
      expect(validarCampoRegistro("telefono", con({ telefono: "12345" }))).toMatch(/escribiste 5/);
      expect(validarCampoRegistro("telefono", con({ telefono: "1".repeat(16) }))).toMatch(/escribiste 16/);
    });
  });

  describe("DNI", () => {
    it("pide entre 6 y 9 números y dice cuántos escribió", () => {
      expect(validarCampoRegistro("dni", con({ dni: "12345" }))).toMatch(/entre 6 y 9 números y escribiste 5/);
      expect(validarCampoRegistro("dni", con({ dni: "1234567890" }))).toMatch(/escribiste 10/);
      expect(validarCampoRegistro("dni", con({ dni: "123456" }))).toBeUndefined();
      expect(validarCampoRegistro("dni", con({ dni: "123456789" }))).toBeUndefined();
    });

    it("vacío pide escribirlo", () => {
      expect(validarCampoRegistro("dni", con({ dni: "" }))).toMatch(/Escribí tu DNI/);
    });
  });

  describe("fecha de nacimiento", () => {
    it("vacía o incompleta pide elegirla", () => {
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "" }))).toMatch(/Elegí tu fecha/);
    });

    it("hoy y las futuras se rechazan; ayer se acepta", () => {
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "2026-10-13" }))).toMatch(/anterior a hoy/);
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "2999-01-01" }))).toMatch(/anterior a hoy/);
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "2026-10-12" }))).toBeUndefined();
    });

    it("un año tipeado mal (anterior a 1900) se rechaza; 1900 se acepta", () => {
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "0198-05-22" }))).toMatch(/anterior a 1900/);
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "1899-12-31" }))).toMatch(/anterior a 1900/);
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "1900-01-01" }))).toBeUndefined();
    });

    it("una fecha imposible se rechaza como inválida", () => {
      expect(validarCampoRegistro("fechaNacimiento", con({ fechaNacimiento: "1998-02-31" }))).toMatch(/no es válida/);
    });
  });

  describe("nombre y apellido", () => {
    it("vacíos piden escribirlos, cada uno con su palabra", () => {
      expect(validarCampoRegistro("nombre", con({ nombre: "  " }))).toBe("Escribí tu nombre.");
      expect(validarCampoRegistro("apellido", con({ apellido: "" }))).toBe("Escribí tu apellido.");
    });

    it("más de 100 caracteres se rechaza", () => {
      expect(validarCampoRegistro("nombre", con({ nombre: "a".repeat(101) }))).toMatch(/máximo son 100/);
      expect(validarCampoRegistro("nombre", con({ nombre: "a".repeat(100) }))).toBeUndefined();
    });
  });
});
