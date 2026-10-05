import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import { registrarManejadorDeSesionVencida } from "../api/client";
import type { RegistroPacienteDatos } from "../api/auth";
import type { Usuario } from "../types";

interface AuthContextValue {
  usuario: Usuario | null;
  estaAutenticado: boolean;
  /** true si la sesión se cerró sola porque el servidor dejó de aceptar el token (venció o la cuenta se desactivó). */
  sesionVencida: boolean;
  login: (email: string, password: string) => Promise<void>;
  registrarPaciente: (datos: RegistroPacienteDatos) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(authApi.obtenerUsuarioGuardado());
  const [sesionVencida, setSesionVencida] = useState(false);

  useEffect(() => {
    // Si el servidor responde 401 a un pedido con sesión, se cierra la sesión acá; las rutas protegidas
    // redirigen solas a /login al ver que ya no hay usuario.
    registrarManejadorDeSesionVencida(() => {
      authApi.cerrarSesion();
      setUsuario(null);
      setSesionVencida(true);
    });
    return () => registrarManejadorDeSesionVencida(null);
  }, []);

  async function login(email: string, password: string) {
    const auth = await authApi.login(email, password);
    authApi.guardarSesion(auth);
    setUsuario({ email: auth.email, nombre: auth.nombre, apellido: auth.apellido, rol: auth.rol });
    setSesionVencida(false);
  }

  async function registrarPaciente(datos: RegistroPacienteDatos) {
    const auth = await authApi.registrarPaciente(datos);
    authApi.guardarSesion(auth);
    setUsuario({ email: auth.email, nombre: auth.nombre, apellido: auth.apellido, rol: auth.rol });
    setSesionVencida(false);
  }

  function logout() {
    authApi.cerrarSesion();
    setUsuario(null);
    setSesionVencida(false);
  }

  return (
    <AuthContext.Provider
      value={{ usuario, estaAutenticado: usuario !== null, sesionVencida, login, registrarPaciente, logout }}
    >
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
