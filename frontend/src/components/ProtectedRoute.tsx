import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import type { Rol } from "../types";

interface Props {
  children: ReactNode;
  /** Si se pasa, además de estar logueado tiene que tener uno de estos roles. */
  rolesPermitidos?: Rol[];
}

export function ProtectedRoute({ children, rolesPermitidos }: Props) {
  const { estaAutenticado, usuario } = useAuth();

  if (!estaAutenticado) {
    return <Navigate to="/login" replace />;
  }
  if (rolesPermitidos && usuario && !rolesPermitidos.includes(usuario.rol)) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
