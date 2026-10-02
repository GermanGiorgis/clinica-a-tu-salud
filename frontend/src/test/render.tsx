import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { AuthProvider } from "../context/AuthContext";
import type { Usuario } from "../types";

interface Opciones {
  /** Ruta inicial; puede llevar `state` para páginas que lo leen de la navegación. */
  ruta?: string | { pathname: string; state?: unknown };
  /** Sesión guardada antes de montar (null = sin sesión iniciada). */
  usuario?: Usuario | null;
  /** Patrón de ruta con el que se monta el elemento (para páginas con :params). */
  path?: string;
}

function MostrarUbicacion() {
  const { pathname } = useLocation();
  return <div data-testid="ubicacion">{pathname}</div>;
}

/**
 * Monta `ui` con router en memoria y AuthProvider. Deja un `data-testid="ubicacion"`
 * con la ruta actual para poder comprobar redirecciones y navegación.
 */
export function renderConSesion(ui: ReactElement, { ruta = "/", usuario = null, path = "*" }: Opciones = {}) {
  if (usuario) {
    localStorage.setItem("usuario", JSON.stringify(usuario));
    localStorage.setItem("token", "token-de-prueba");
  }
  return render(
    <MemoryRouter initialEntries={[ruta]}>
      <AuthProvider>
        <Routes>
          <Route path={path} element={ui} />
          <Route path="*" element={<div>otra-pagina</div>} />
        </Routes>
        <MostrarUbicacion />
      </AuthProvider>
    </MemoryRouter>,
  );
}
