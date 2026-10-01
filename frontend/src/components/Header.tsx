import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./Header.css";

export function Header() {
  const { usuario, estaAutenticado, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <header className="header">
      <Link to="/" className="header__marca">Clínica</Link>
      <nav className="header__nav">
        <Link to="/medicos">Médicos</Link>
        {estaAutenticado && usuario?.rol === "PACIENTE" && <Link to="/mis-turnos">Mis turnos</Link>}
        {estaAutenticado ? (
          <>
            <span className="header__usuario">{usuario?.nombre} · {usuario?.rol}</span>
            <button type="button" onClick={handleLogout}>Salir</button>
          </>
        ) : (
          <>
            <Link to="/login">Iniciar sesión</Link>
            <Link to="/registro">Registrarme</Link>
          </>
        )}
      </nav>
    </header>
  );
}
