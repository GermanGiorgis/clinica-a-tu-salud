import { beforeEach, describe, expect, it } from "vitest";
import { cerrarSesion, guardarSesion, obtenerUsuarioGuardado, type AuthResponse } from "./auth";

const auth: AuthResponse = { token: "jwt-123", email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", rol: "PACIENTE" };

describe("sesión en localStorage", () => {
  beforeEach(() => localStorage.clear());

  it("sin sesión guardada no hay usuario", () => {
    expect(obtenerUsuarioGuardado()).toBeNull();
  });

  it("guardarSesion guarda el token y el usuario, y el usuario no incluye el token", () => {
    guardarSesion(auth);

    expect(localStorage.getItem("token")).toBe("jwt-123");
    expect(obtenerUsuarioGuardado()).toEqual({ email: "ana@clinica.test", nombre: "Ana", apellido: "Paz", rol: "PACIENTE" });
  });

  it("cerrarSesion borra el token y el usuario", () => {
    guardarSesion(auth);

    cerrarSesion();

    expect(localStorage.getItem("token")).toBeNull();
    expect(obtenerUsuarioGuardado()).toBeNull();
  });
});
