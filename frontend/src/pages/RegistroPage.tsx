import { useRef, useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { isAxiosError } from "axios";
import { useAuth } from "../context/AuthContext";
import { soloDigitos } from "../utils/dni";
import { capitalizar, erroresPorCampo, extraerError } from "../utils/errores";
import {
  CAMPOS_REGISTRO, validarCampoRegistro, validarRegistro,
  type CampoRegistro, type DatosRegistro, type ErroresRegistro,
} from "../utils/validacionRegistro";
import "../components/AuthForm.css";

const FORM_VACIO: DatosRegistro = { nombre: "", apellido: "", email: "", password: "", telefono: "", dni: "", fechaNacimiento: "" };

const AVISO_REVISAR = "Revisá los datos marcados en rojo: no se creó la cuenta.";

export function RegistroPage() {
  const { registrarPaciente } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState<DatosRegistro>(FORM_VACIO);
  const [errores, setErrores] = useState<ErroresRegistro>({});
  const [emailYaExiste, setEmailYaExiste] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  function cambiar(nombre: CampoRegistro, valor: string) {
    const nuevo = { ...form, [nombre]: valor };
    setForm(nuevo);
    if (nombre === "email") setEmailYaExiste(false);
    // Un campo ya marcado se vuelve a evaluar mientras se corrige, para que el aviso desaparezca apenas queda bien.
    if (errores[nombre]) setErrores({ ...errores, [nombre]: validarCampoRegistro(nombre, nuevo) });
  }

  /**
   * Al salir de un campo se lo revisa en el momento, sin esperar al botón. Solo agrega avisos: uno que ya está
   * puesto (por ejemplo, el que mandó el servidor) se quita al corregir el campo, no por pasar de largo.
   */
  function alSalir(nombre: CampoRegistro) {
    const mensaje = validarCampoRegistro(nombre, form);
    if (mensaje) setErrores((previos) => ({ ...previos, [nombre]: mensaje }));
  }

  function enfocarPrimerError(conErrores: ErroresRegistro) {
    const primero = CAMPOS_REGISTRO.find((campo) => conErrores[campo]);
    if (primero) formRef.current?.querySelector<HTMLElement>(`[name="${primero}"]`)?.focus();
  }

  function campo(nombre: CampoRegistro, extra?: { onChange?: (e: ChangeEvent<HTMLInputElement>) => void }) {
    return {
      name: nombre,
      value: form[nombre],
      onChange: extra?.onChange ?? ((e: ChangeEvent<HTMLInputElement>) => cambiar(nombre, e.target.value)),
      onBlur: () => alSalir(nombre),
      "aria-invalid": errores[nombre] ? true : undefined,
      "aria-describedby": errores[nombre] ? `error-${nombre}` : undefined,
    } as const;
  }

  function conError(nombre: CampoRegistro, hijo: ReactNode) {
    return (
      <div className="auth-form__campo">
        {hijo}
        {errores[nombre] && (
          <p id={`error-${nombre}`} className="auth-form__campo-error" role="alert">
            <span>{errores[nombre]}</span>
            {nombre === "email" && emailYaExiste && <> <Link to="/login">Iniciá sesión</Link></>}
          </p>
        )}
      </div>
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const pendientes = validarRegistro(form);
    setErrores(pendientes);
    if (Object.keys(pendientes).length > 0) {
      setError(AVISO_REVISAR);
      enfocarPrimerError(pendientes);
      return;
    }
    setCargando(true);
    try {
      await registrarPaciente({
        ...form,
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim(),
        email: form.email.trim(),
        telefono: form.telefono.trim() || undefined,
      });
      navigate("/");
    } catch (err) {
      mostrarFallaDelServidor(err);
    } finally {
      setCargando(false);
    }
  }

  /** Lo que el servidor rechaza se pone debajo del campo que corresponde; lo que no es de un campo, arriba del botón. */
  function mostrarFallaDelServidor(err: unknown) {
    const porCampo = erroresPorCampo(err);
    if (porCampo) {
      const deCampos: ErroresRegistro = {};
      const otros: Record<string, string> = {};
      for (const [clave, mensaje] of Object.entries(porCampo)) {
        if ((CAMPOS_REGISTRO as string[]).includes(clave)) deCampos[clave as CampoRegistro] = capitalizar(mensaje);
        else otros[clave] = mensaje;
      }
      setErrores(deCampos);
      setError(Object.keys(otros).length > 0 ? extraerError(err) : AVISO_REVISAR);
      enfocarPrimerError(deCampos);
      return;
    }
    if (isAxiosError(err) && err.response?.status === 409) {
      // El único choque posible al registrarse es el email repetido.
      setErrores({ email: extraerError(err) });
      setEmailYaExiste(true);
      setError(null);
      enfocarPrimerError({ email: "x" });
      return;
    }
    setError(extraerError(err));
  }

  return (
    <main className="auth-form">
      <h1>Crear cuenta de paciente</h1>
      <form ref={formRef} onSubmit={handleSubmit} noValidate>
        <div className="auth-form__fila">
          {conError("nombre", <label>Nombre<input autoComplete="given-name" {...campo("nombre")} /></label>)}
          {conError("apellido", <label>Apellido<input autoComplete="family-name" {...campo("apellido")} /></label>)}
        </div>
        {conError("email", <label>Email<input type="email" autoComplete="email" {...campo("email")} /></label>)}
        {conError("password", (
          <label>
            Contraseña (entre 10 y 72 caracteres)
            <input type="password" minLength={10} maxLength={72} autoComplete="new-password" {...campo("password")} />
          </label>
        ))}
        {conError("telefono", <label>Teléfono (opcional)<input type="tel" autoComplete="tel" {...campo("telefono")} /></label>)}
        <div className="auth-form__fila">
          {conError("dni", (
            <label>
              DNI (solo números)
              <input
                inputMode="numeric" autoComplete="off" maxLength={9}
                {...campo("dni", { onChange: (e) => cambiar("dni", soloDigitos(e.target.value)) })}
              />
            </label>
          ))}
          {conError("fechaNacimiento", <label>Fecha de nacimiento<input type="date" max={maxNacimiento()} autoComplete="bday" {...campo("fechaNacimiento")} /></label>)}
        </div>
        {error && <p className="auth-form__error" role="alert">{error}</p>}
        {/* Sin foco en el botón al apretarlo: si el aviso de un campo apareciera al salir de él, correría el botón y el clic se perdería. */}
        <button type="submit" className="btn btn-primary" disabled={cargando} onMouseDown={(e) => e.preventDefault()}>{cargando ? "Creando cuenta…" : "Crear cuenta"}</button>
      </form>
      <p className="auth-form__nota">
        Para que te puedan dar turnos y ver tu historial, la clínica tiene que verificar tu identidad:
        llevá tu DNI la primera vez que vengas.
      </p>
      <p>¿Ya tenés cuenta? <Link to="/login">Iniciá sesión</Link></p>
    </main>
  );
}

/** Ayuda del selector de fecha: no deja elegir de hoy en adelante (el mensaje de arriba cubre lo que se escriba a mano). */
function maxNacimiento(): string {
  const ayer = new Date();
  ayer.setDate(ayer.getDate() - 1);
  return ayer.toLocaleDateString("sv-SE");
}
