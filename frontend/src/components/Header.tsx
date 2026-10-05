import { Link, useLocation, useNavigate } from "react-router-dom";
import { Calendar, ChartBar, ClipboardText, ListMagnifyingGlass, ShieldCheck, SignOut, Stethoscope, UserCheck, UserCircle } from "@phosphor-icons/react";
import { useAuth } from "../context/AuthContext";
import "./Header.css";

export function Header() {
  const { usuario, estaAutenticado, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  function handleLogout() {
    logout();
    navigate("/");
  }

  function activo(ruta: string) {
    return location.pathname === ruta || location.pathname.startsWith(`${ruta}/`);
  }

  return (
    <header className="header">
      <Link to="/" className="header__marca">
        <Stethoscope size={22} weight="bold" aria-hidden="true" />
        Clínica
      </Link>
      <nav className="header__nav">
        <Link to="/medicos" className={activo("/medicos") ? "activo" : ""}>Médicos</Link>
        {estaAutenticado && usuario?.rol === "PACIENTE" && (
          <Link to="/mis-turnos" className={activo("/mis-turnos") ? "activo" : ""}>
            <Calendar size={18} aria-hidden="true" /> Mis turnos
          </Link>
        )}
        {estaAutenticado && usuario?.rol === "PACIENTE" && (
          <Link to="/historial" className={activo("/historial") ? "activo" : ""}>
            <ClipboardText size={18} aria-hidden="true" /> Mi historial
          </Link>
        )}
        {estaAutenticado && usuario?.rol === "PACIENTE" && (
          <Link to="/mi-cobertura" className={activo("/mi-cobertura") ? "activo" : ""}>
            <ShieldCheck size={18} aria-hidden="true" /> Mi cobertura
          </Link>
        )}
        {estaAutenticado && (usuario?.rol === "MEDICO" || usuario?.rol === "ADMIN") && (
          <Link to="/agenda" className={activo("/agenda") ? "activo" : ""}>
            <Calendar size={18} aria-hidden="true" /> Mi agenda
          </Link>
        )}
        {estaAutenticado && usuario?.rol === "ADMIN" && (
          <Link to="/metricas" className={activo("/metricas") ? "activo" : ""}>
            <ChartBar size={18} aria-hidden="true" /> Métricas
          </Link>
        )}
        {estaAutenticado && usuario?.rol === "ADMIN" && (
          <Link to="/pacientes/pendientes" className={activo("/pacientes/pendientes") ? "activo" : ""}>
            <UserCheck size={18} aria-hidden="true" /> Verificar pacientes
          </Link>
        )}
        {estaAutenticado && usuario?.rol === "ADMIN" && (
          <Link to="/auditoria" className={activo("/auditoria") ? "activo" : ""}>
            <ListMagnifyingGlass size={18} aria-hidden="true" /> Auditoría
          </Link>
        )}
        {estaAutenticado ? (
          <>
            <span className="header__usuario">
              <UserCircle size={20} aria-hidden="true" />
              <span className="header__nombre">{usuario?.nombre}</span>
              <span className="header__rol">{usuario?.rol}</span>
            </span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout}>
              <SignOut size={16} aria-hidden="true" /> Salir
            </button>
          </>
        ) : (
          <>
            <Link to="/login">Iniciar sesión</Link>
            <Link to="/registro" className="btn btn-primary btn-sm">Registrarme</Link>
          </>
        )}
      </nav>
    </header>
  );
}
