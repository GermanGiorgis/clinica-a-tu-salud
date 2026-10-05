import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { extraerError } from "../utils/errores";
import "../components/AuthForm.css";

export function LoginPage() {
  const { login, sesionVencida } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      await login(email, password);
      navigate("/");
    } catch (err) {
      setError(extraerError(err, "Email o contraseña incorrectos"));
    } finally {
      setCargando(false);
    }
  }

  return (
    <main className="auth-form">
      <h1>Iniciar sesión</h1>
      {sesionVencida && (
        <p className="auth-form__aviso" role="status">Tu sesión se cerró (venció o la cuenta fue desactivada). Iniciá sesión de nuevo.</p>
      )}
      <form onSubmit={handleSubmit}>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Contraseña
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="auth-form__error">{error}</p>}
        <button type="submit" className="btn btn-primary" disabled={cargando}>{cargando ? "Ingresando…" : "Ingresar"}</button>
      </form>
      <p>¿No tenés cuenta? <Link to="/registro">Registrate como paciente</Link></p>
    </main>
  );
}
