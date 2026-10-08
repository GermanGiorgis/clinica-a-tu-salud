import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { usuario } from "../test/fixtures";
import { renderConSesion } from "../test/render";
import { HomePage } from "./HomePage";

describe("HomePage", () => {
  it("a quien no tiene sesión le ofrece buscar por especialidad y crear una cuenta", () => {
    renderConSesion(<HomePage />, { ruta: "/", path: "/" });

    expect(screen.getByRole("link", { name: "Buscar por especialidad" })).toHaveAttribute("href", "/medicos");
    expect(screen.getByRole("link", { name: "Crear cuenta de paciente" })).toHaveAttribute("href", "/registro");
  });

  it("a quien ya inició sesión no le ofrece crear otra cuenta", () => {
    renderConSesion(<HomePage />, { usuario: usuario("PACIENTE"), ruta: "/", path: "/" });

    expect(screen.getByRole("link", { name: "Buscar por especialidad" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Crear cuenta de paciente" })).not.toBeInTheDocument();
  });

  it("presenta los cuatro roles, incluida la recepción", () => {
    renderConSesion(<HomePage />, { ruta: "/", path: "/" });

    for (const rol of ["Paciente", "Médico", "Recepción", "Admin"]) {
      expect(screen.getByRole("heading", { name: rol, level: 3 })).toBeInTheDocument();
    }
  });
});
