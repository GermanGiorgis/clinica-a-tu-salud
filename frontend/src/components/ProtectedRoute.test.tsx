import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AuthProvider } from "../context/AuthContext";
import { usuario } from "../test/fixtures";
import type { Rol, Usuario } from "../types";
import { ProtectedRoute } from "./ProtectedRoute";

function montar(sesion: Usuario | null, rolesPermitidos?: Rol[]) {
  if (sesion) {
    localStorage.setItem("usuario", JSON.stringify(sesion));
    localStorage.setItem("token", "token-de-prueba");
  }
  render(
    <MemoryRouter initialEntries={["/privada"]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<p>pagina-inicio</p>} />
          <Route path="/login" element={<p>pagina-login</p>} />
          <Route
            path="/privada"
            element={
              <ProtectedRoute rolesPermitidos={rolesPermitidos}>
                <p>contenido-privado</p>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("sin sesión redirige a /login y no muestra el contenido", () => {
    montar(null);

    expect(screen.getByText("pagina-login")).toBeInTheDocument();
    expect(screen.queryByText("contenido-privado")).not.toBeInTheDocument();
  });

  it("con sesión y sin restricción de rol muestra el contenido", () => {
    montar(usuario("PACIENTE"));

    expect(screen.getByText("contenido-privado")).toBeInTheDocument();
  });

  it("con un rol permitido muestra el contenido", () => {
    montar(usuario("MEDICO"), ["MEDICO", "ADMIN"]);

    expect(screen.getByText("contenido-privado")).toBeInTheDocument();
  });

  it("con un rol no permitido redirige al inicio y no muestra el contenido", () => {
    montar(usuario("PACIENTE"), ["MEDICO", "ADMIN"]);

    expect(screen.getByText("pagina-inicio")).toBeInTheDocument();
    expect(screen.queryByText("contenido-privado")).not.toBeInTheDocument();
  });

  it("sin sesión y con roles exigidos manda a /login (no al inicio)", () => {
    montar(null, ["ADMIN"]);

    expect(screen.getByText("pagina-login")).toBeInTheDocument();
  });
});
