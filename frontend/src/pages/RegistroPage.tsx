import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { extraerError } from "../utils/errores";
import "../components/AuthForm.css";

export function RegistroPage() {
  const { registrarPaciente } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    nombre: "", apellido: "", email: "", password: "", telefono: "", dni: "", fechaNacimiento: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  function campo(nombre: keyof typeof form) {
    return {
      value: form[nombre],
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [nombre]: e.target.value }),
    };
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      await registrarPaciente({ ...form, telefono: form.telefono || undefined });
      navigate("/");
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setCargando(false);
    }
  }

  return (
    <main className="auth-form">
      <h1>Crear cuenta de paciente</h1>
      <form onSubmit={handleSubmit}>
        <div className="auth-form__fila">
          <label>Nombre<input required {...campo("nombre")} /></label>
          <label>Apellido<input required {...campo("apellido")} /></label>
        </div>
        <label>Email<input type="email" required {...campo("email")} /></label>
        <label>Contraseña (mínimo 6 caracteres)<input type="password" minLength={6} required {...campo("password")} /></label>
        <label>Teléfono (opcional)<input {...campo("telefono")} /></label>
        <div className="auth-form__fila">
          <label>DNI<input required {...campo("dni")} /></label>
          <label>Fecha de nacimiento<input type="date" required {...campo("fechaNacimiento")} /></label>
        </div>
        {error && <p className="auth-form__error">{error}</p>}
        <button type="submit" disabled={cargando}>{cargando ? "Creando cuenta…" : "Crear cuenta"}</button>
      </form>
      <p>¿Ya tenés cuenta? <Link to="/login">Iniciá sesión</Link></p>
    </main>
  );
}
