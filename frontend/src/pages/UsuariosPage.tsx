import { useEffect, useMemo, useState, type FormEvent } from "react";
import { MagnifyingGlass, UserCirclePlus, UserMinus, UserPlus } from "@phosphor-icons/react";
import { crearUsuario, cambiarActivo, listarUsuarios } from "../api/usuarios";
import { listarEspecialidades } from "../api/especialidades";
import { useAuth } from "../context/AuthContext";
import { soloDigitos } from "../utils/dni";
import { normalizar } from "../utils/especialidades";
import { extraerError } from "../utils/errores";
import type { Especialidad, Rol, UsuarioListado } from "../types";
import "./UsuariosPage.css";

const ETIQUETA_ROL: Record<Rol, string> = {
  PACIENTE: "Paciente",
  MEDICO: "Médico",
  RECEPCIONISTA: "Recepción",
  ADMIN: "Administrador",
};

const FORM_VACIO = {
  rol: "RECEPCIONISTA" as Rol,
  nombre: "", apellido: "", email: "", password: "", telefono: "",
  dni: "", fechaNacimiento: "", matricula: "",
};

/**
 * Alta y baja de cuentas. Las cuentas no se borran: con historia clínica de por medio no se puede, así que
 * "dar de baja" es desactivar (reversible). Una cuenta desactivada no puede entrar, y si es de un médico
 * deja de aparecer para los pacientes.
 */
export function UsuariosPage() {
  const { usuario: yo } = useAuth();
  const [usuarios, setUsuarios] = useState<UsuarioListado[] | null>(null);
  const [especialidades, setEspecialidades] = useState<Especialidad[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [filtroRol, setFiltroRol] = useState<"" | Rol>("");
  const [busqueda, setBusqueda] = useState("");
  const [procesando, setProcesando] = useState<number | null>(null);

  const [creando, setCreando] = useState(false);
  const [form, setForm] = useState(FORM_VACIO);
  const [especialidadIds, setEspecialidadIds] = useState<number[]>([]);
  const [guardando, setGuardando] = useState(false);
  const [errorForm, setErrorForm] = useState<string | null>(null);

  function cargar() {
    listarUsuarios()
      .then(setUsuarios)
      .catch((err) => setError(extraerError(err, "No se pudo cargar la lista de usuarios.")));
  }

  useEffect(() => {
    cargar();
    listarEspecialidades().then(setEspecialidades).catch(() => {});
  }, []);

  const visibles = useMemo(() => {
    const termino = normalizar(busqueda);
    return (usuarios ?? []).filter(
      (u) =>
        (!filtroRol || u.rol === filtroRol) &&
        (!termino || normalizar(`${u.nombre} ${u.apellido} ${u.email}`).includes(termino)),
    );
  }, [usuarios, filtroRol, busqueda]);

  async function handleActivo(u: UsuarioListado) {
    const baja = u.activo;
    if (baja && !window.confirm(
      `¿Dar de baja a ${u.nombre} ${u.apellido}? No va a poder iniciar sesión${u.rol === "MEDICO" ? " y los pacientes dejan de verlo" : ""}. Se puede reactivar cuando quieras.`,
    )) {
      return;
    }
    setProcesando(u.id);
    setError(null);
    setAviso(null);
    try {
      await cambiarActivo(u.id, !baja);
      setAviso(baja ? `${u.nombre} ${u.apellido} quedó dado de baja.` : `${u.nombre} ${u.apellido} volvió a estar activo.`);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setProcesando(null);
    }
  }

  async function handleCrear(e: FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setErrorForm(null);
    setAviso(null);
    try {
      const creado = await crearUsuario({
        rol: form.rol,
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim(),
        email: form.email.trim(),
        password: form.password,
        telefono: form.telefono.trim() || undefined,
        ...(form.rol === "PACIENTE" ? { dni: form.dni, fechaNacimiento: form.fechaNacimiento } : {}),
        ...(form.rol === "MEDICO" ? { matricula: form.matricula.trim(), especialidadIds } : {}),
      });
      setAviso(`Se creó la cuenta de ${creado.nombre} ${creado.apellido} (${ETIQUETA_ROL[creado.rol]}).`);
      setForm({ ...FORM_VACIO, rol: form.rol });
      setEspecialidadIds([]);
      setCreando(false);
      cargar();
    } catch (err) {
      setErrorForm(extraerError(err, "No se pudo crear la cuenta."));
    } finally {
      setGuardando(false);
    }
  }

  function alternarEspecialidad(id: number) {
    setEspecialidadIds((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  const texto = (campo: keyof typeof form) => ({
    value: form[campo],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [campo]: e.target.value }),
  });

  return (
    <main className="page">
      <h1>Usuarios</h1>
      <p className="usuarios__intro">
        Creá cuentas de médicos, recepción, pacientes o administradores. Las cuentas no se borran: se dan de baja
        (y se pueden reactivar), porque quedan asociadas a turnos e historias clínicas.
      </p>

      {error && <p className="error-banner">{error}</p>}
      {aviso && <p className="usuarios__aviso">{aviso}</p>}

      {!creando ? (
        <button type="button" className="btn btn-primary" onClick={() => { setCreando(true); setAviso(null); }}>
          <UserPlus size={18} aria-hidden="true" /> Nuevo usuario
        </button>
      ) : (
        <form className="card usuarios__form" onSubmit={handleCrear} aria-label="Nuevo usuario">
          <label>
            Tipo de cuenta
            <select value={form.rol} onChange={(e) => setForm({ ...form, rol: e.target.value as Rol })}>
              {(["MEDICO", "RECEPCIONISTA", "PACIENTE", "ADMIN"] as Rol[]).map((r) => (
                <option key={r} value={r}>{ETIQUETA_ROL[r]}</option>
              ))}
            </select>
          </label>
          <label>Nombre<input required maxLength={100} {...texto("nombre")} /></label>
          <label>Apellido<input required maxLength={100} {...texto("apellido")} /></label>
          <label>Email<input type="email" required maxLength={255} {...texto("email")} /></label>
          <label>Contraseña (entre 10 y 72 caracteres)<input type="password" required minLength={10} maxLength={72} autoComplete="new-password" {...texto("password")} /></label>
          <label>Teléfono (opcional)<input maxLength={30} {...texto("telefono")} /></label>

          {form.rol === "PACIENTE" && (
            <>
              <label>
                DNI (solo números)
                <input required inputMode="numeric" minLength={6} maxLength={9} value={form.dni}
                  onChange={(e) => setForm({ ...form, dni: soloDigitos(e.target.value) })} />
              </label>
              <label>Fecha de nacimiento<input type="date" required {...texto("fechaNacimiento")} /></label>
            </>
          )}

          {form.rol === "MEDICO" && (
            <>
              <label>Matrícula<input required maxLength={30} {...texto("matricula")} /></label>
              <fieldset className="usuarios__especialidades">
                <legend>Especialidades</legend>
                {especialidades.length === 0 && <p className="empty-state">No hay especialidades cargadas.</p>}
                {especialidades.map((esp) => (
                  <label key={esp.id} className="usuarios__check">
                    <input type="checkbox" checked={especialidadIds.includes(esp.id)} onChange={() => alternarEspecialidad(esp.id)} />
                    {esp.nombre}
                  </label>
                ))}
              </fieldset>
            </>
          )}

          {errorForm && <p className="error-banner">{errorForm}</p>}
          <div className="usuarios__acciones-form">
            <button type="submit" className="btn btn-primary" disabled={guardando}>
              <UserCirclePlus size={18} aria-hidden="true" /> {guardando ? "Creando…" : "Crear cuenta"}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => { setCreando(false); setErrorForm(null); }}>Cancelar</button>
          </div>
        </form>
      )}

      <div className="usuarios__filtros">
        <label>
          <span><MagnifyingGlass size={14} aria-hidden="true" /> Buscar</span>
          <input type="search" placeholder="Nombre o email" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </label>
        <label>
          Mostrar
          <select value={filtroRol} onChange={(e) => setFiltroRol(e.target.value as "" | Rol)}>
            <option value="">Todos los tipos</option>
            {(Object.keys(ETIQUETA_ROL) as Rol[]).map((r) => (
              <option key={r} value={r}>{ETIQUETA_ROL[r]}</option>
            ))}
          </select>
        </label>
      </div>

      {usuarios === null && !error && <p className="empty-state">Cargando…</p>}
      {usuarios !== null && visibles.length === 0 && <p className="empty-state">No hay usuarios que coincidan.</p>}
      <ul className="usuarios__lista">
        {visibles.map((u) => (
          <li key={u.id} className={`card usuarios__item${u.activo ? "" : " usuarios__item--baja"}`}>
            <div className="usuarios__datos">
              <strong>{u.nombre} {u.apellido}</strong>
              <span>{u.email}</span>
              {u.telefono && <span>Tel. {u.telefono}</span>}
            </div>
            <div className="usuarios__estado">
              <span className="usuarios__rol">{ETIQUETA_ROL[u.rol]}</span>
              <span className={u.activo ? "badge badge--confirmado" : "badge badge--cancelado"}>{u.activo ? "Activo" : "De baja"}</span>
              {u.email.toLowerCase() !== yo?.email.toLowerCase() && (
                <button
                  type="button"
                  className={u.activo ? "btn btn-danger btn-sm" : "btn btn-secondary btn-sm"}
                  disabled={procesando === u.id}
                  onClick={() => handleActivo(u)}
                  aria-label={`${u.activo ? "Dar de baja" : "Reactivar"} a ${u.nombre} ${u.apellido}`}
                >
                  <UserMinus size={16} aria-hidden="true" /> {u.activo ? "Dar de baja" : "Reactivar"}
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </main>
  );
}
