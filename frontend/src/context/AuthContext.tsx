import { createContext, useContext, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import type { RegistroPacienteDatos } from "../api/auth";
import type { Usuario } from "../types";

interface AuthContextValue {
  usuario: Usuario | null;
  estaAutenticado: boolean;
  login: (email: string, password: string) => Promise<void>;
  registrarPaciente: (datos: RegistroPacienteDatos) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(authApi.obtenerUsuarioGuardado());

  async function login(email: string, password: string) {
    const auth = await authApi.login(email, password);
    authApi.guardarSesion(auth);
    setUsuario({ email: auth.email, nombre: auth.nombre, apellido: auth.apellido, rol: auth.rol });
  }

  async function registrarPaciente(datos: RegistroPacienteDatos) {
    const auth = await authApi.registrarPaciente(datos);
    authApi.guardarSesion(auth);
    setUsuario({ email: auth.email, nombre: auth.nombre, apellido: auth.apellido, rol: auth.rol });
  }

  function logout() {
    authApi.cerrarSesion();
    setUsuario(null);
  }

  return (
    <AuthContext.Provider value={{ usuario, estaAutenticado: usuario !== null, login, registrarPaciente, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth debe usarse dentro de un AuthProvider");
  }
  return context;
}
