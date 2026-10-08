import { Link, useLocation, useNavigate } from "react-router-dom";
import { Calendar, CalendarX, ChartBar, ClipboardText, ListMagnifyingGlass, ShieldCheck, SignOut, Stethoscope, UserCheck, UserCircle, UsersThree } from "@phosphor-icons/react";
import { useAuth } from "../context/AuthContext";
import { esPersonalDeMostrador } from "../utils/roles";
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
        <Link to="/medicos" className={activo("/medicos") ? "activo" : ""}>Especialidades</Link>
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
        {estaAutenticado && (usuario?.rol === "MEDICO" || esPersonalDeMostrador(usuario?.rol)) && (
          <Link to="/agenda" className={activo("/agenda") ? "activo" : ""}>
            <Calendar size={18} aria-hidden="true" /> {usuario?.rol === "MEDICO" ? "Mi agenda" : "Agenda"}
          </Link>
        )}
        {estaAutenticado && esPersonalDeMostrador(usuario?.rol) && (
          <Link to="/dias-no-laborables" className={activo("/dias-no-laborables") ? "activo" : ""}>
            <CalendarX size={18} aria-hidden="true" /> Feriados y ausencias
          </Link>
        )}
        {estaAutenticado && usuario?.rol === "ADMIN" && (
          <Link to="/usuarios" className={activo("/usuarios") ? "activo" : ""}>
            <UsersThree size={18} aria-hidden="true" /> Usuarios
          </Link>
        )}
        {estaAutenticado && usuario?.rol === "ADMIN" && (
          <Link to="/metricas" className={activo("/metricas") ? "activo" : ""}>
            <ChartBar size={18} aria-hidden="true" /> Métricas
          </Link>
        )}
        {estaAutenticado && esPersonalDeMostrador(usuario?.rol) && (
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
