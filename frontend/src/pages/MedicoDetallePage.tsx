import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { CalendarBlank, CheckCircle, Stethoscope } from "@phosphor-icons/react";
import { obtenerDisponibilidad, obtenerMedico } from "../api/medicos";
import { crearTurno } from "../api/turnos";
import { misAfiliaciones } from "../api/afiliaciones";
import { useAuth } from "../context/AuthContext";
import { extraerError } from "../utils/errores";
import { esPersonalDeLaClinica } from "../utils/roles";
import type { Afiliacion, Medico } from "../types";
import "./MedicoDetallePage.css";

/** Cuántos días hacia adelante se puede reservar (el backend rechaza más allá: ver VENTANA_RESERVA_DIAS). */
const VENTANA_RESERVA_DIAS = 60;

function hoyISO(): string {
  return new Date().toLocaleDateString("sv-SE"); // sv-SE = formato YYYY-MM-DD
}

function ultimoDiaReservableISO(): string {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() + VENTANA_RESERVA_DIAS);
  return fecha.toLocaleDateString("sv-SE");
}

export function MedicoDetallePage() {
  const { id } = useParams<{ id: string }>();
  const medicoId = Number(id);
  const { usuario, estaAutenticado } = useAuth();
  const navigate = useNavigate();

  const [medico, setMedico] = useState<Medico | null>(null);
  const [fecha, setFecha] = useState(hoyISO());
  const [horarios, setHorarios] = useState<string[] | null>(null);
  /** Si el día está cerrado (feriado, ausencia del médico), por qué: se muestra en lugar de "no hay horarios". */
  const [aviso, setAviso] = useState<string | null>(null);
  const [horarioElegido, setHorarioElegido] = useState<string | null>(null);
  const [motivoConsulta, setMotivoConsulta] = useState("");
  const [pacienteDni, setPacienteDni] = useState("");
  const [afiliaciones, setAfiliaciones] = useState<Afiliacion[]>([]);
  const [afiliacionId, setAfiliacionId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [mensajeOk, setMensajeOk] = useState<string | null>(null);
  const [reservando, setReservando] = useState(false);

  useEffect(() => {
    obtenerMedico(medicoId)
      .then(setMedico)
      .catch(() => setError("No se encontró el médico."));
  }, [medicoId]);

  useEffect(() => {
    if (usuario?.rol === "PACIENTE") {
      misAfiliaciones().then(setAfiliaciones).catch(() => {});
    }
  }, [usuario]);

  useEffect(() => {
    setHorarios(null);
    setAviso(null);
    setHorarioElegido(null);
    obtenerDisponibilidad(medicoId, fecha)
      .then((d) => {
        setHorarios(d.horariosLibres);
        setAviso(d.aviso ?? null);
      })
      .catch(() => setError("No se pudo calcular la disponibilidad."));
  }, [medicoId, fecha]);

  async function reservar() {
    if (!horarioElegido) return;
    setReservando(true);
    setError(null);
    setMensajeOk(null);
    try {
      await crearTurno({
        medicoId,
        inicio: `${fecha}T${horarioElegido}`,
        motivoConsulta: motivoConsulta || undefined,
        pacienteDni: esStaff ? pacienteDni : undefined,
        afiliacionId: afiliacionId ? Number(afiliacionId) : undefined,
      });
      setMensajeOk(esStaff ? "¡Turno reservado para ese paciente!" : "¡Turno reservado! Lo vas a ver en \"Mis turnos\".");
      setHorarioElegido(null);
      setMotivoConsulta("");
      setPacienteDni("");
      setAfiliacionId("");
      const d = await obtenerDisponibilidad(medicoId, fecha);
      setHorarios(d.horariosLibres);
      setAviso(d.aviso ?? null);
    } catch (err) {
      setError(extraerError(err, "No se pudo reservar el turno."));
      // Casi siempre es un 409: otra persona se llevó ese horario. Se vuelve a pedir la disponibilidad
      // para que el horario ocupado deje de verse (y el usuario no lo reintente en vano).
      setHorarioElegido(null);
      obtenerDisponibilidad(medicoId, fecha)
        .then((d) => {
          setHorarios(d.horariosLibres);
          setAviso(d.aviso ?? null);
        })
        .catch(() => {});
    } finally {
      setReservando(false);
    }
  }

  if (!medico) {
    return <main className="page">{error ? <p className="error-banner">{error}</p> : <p className="empty-state">Cargando…</p>}</main>;
  }

  const esStaff = esPersonalDeLaClinica(usuario?.rol);
  const puedeReservar = usuario?.rol === "PACIENTE" || esStaff;

  return (
    <main className="page">
      <Link to="/medicos" className="volver">← Volver a médicos</Link>
      <div className="medico-detalle__cabecera">
        <span className="medico-detalle__avatar"><Stethoscope size={28} weight="duotone" aria-hidden="true" /></span>
        <div>
          <h1>{medico.nombre} {medico.apellido}</h1>
          <p className="medico-detalle__info">
            Matrícula {medico.matricula}
            {medico.especialidades.length > 0 && <> · {medico.especialidades.map((e) => e.nombre).join(", ")}</>}
          </p>
        </div>
      </div>

      <label className="medico-detalle__fecha">
        <CalendarBlank size={16} aria-hidden="true" /> Fecha
        <input type="date" value={fecha} min={hoyISO()} max={ultimoDiaReservableISO()} onChange={(e) => setFecha(e.target.value)} />
      </label>

      <h2>Horarios disponibles</h2>
      {horarios === null && <p className="empty-state">Calculando…</p>}
      {horarios !== null && horarios.length === 0 && (
        <p className={aviso ? "medico-detalle__cerrado" : "empty-state"}>{aviso ?? "No hay horarios libres ese día."}</p>
      )}
      <ul className="medico-detalle__horarios">
        {horarios?.map((h) => (
          <li key={h}>
            <button
              type="button"
              className={horarioElegido === h ? "activo" : ""}
              onClick={() => setHorarioElegido(h)}
            >
              {h.slice(0, 5)}
            </button>
          </li>
        ))}
      </ul>

      {horarioElegido && (
        <div className="medico-detalle__reserva">
          {!estaAutenticado && (
            <p>
              Para reservar necesitás <Link to="/login">iniciar sesión</Link> o{" "}
              <Link to="/registro">crear una cuenta</Link>.
            </p>
          )}
          {estaAutenticado && !puedeReservar && (
            <p>Tu cuenta no puede reservar turnos.</p>
          )}
          {estaAutenticado && puedeReservar && (
            <>
              {esStaff && (
                <label>
                  DNI del paciente
                  <input className="input" value={pacienteDni} onChange={(e) => setPacienteDni(e.target.value)} />
                </label>
              )}
              {!esStaff && afiliaciones.length > 0 && (
                <label>
                  Cobertura (opcional)
                  <select value={afiliacionId} onChange={(e) => setAfiliacionId(e.target.value)}>
                    <option value="">Particular (sin cobertura)</option>
                    {afiliaciones.map((a) => (
                      <option key={a.id} value={a.id}>{a.obraSocialNombre} — N° {a.numeroAfiliado}</option>
                    ))}
                  </select>
                </label>
              )}
              <label>
                Motivo de la consulta (opcional)
                <textarea className="input" value={motivoConsulta} onChange={(e) => setMotivoConsulta(e.target.value)} rows={2} />
              </label>
              <button type="button" className="btn btn-primary" onClick={reservar} disabled={reservando || (esStaff && !pacienteDni)}>
                {reservando ? "Reservando…" : `Reservar ${fecha} ${horarioElegido.slice(0, 5)}`}
              </button>
            </>
          )}
        </div>
      )}

      {error && <p className="error-banner">{error}</p>}
      {mensajeOk && (
        <p className="medico-detalle__ok">
          <CheckCircle size={18} weight="fill" aria-hidden="true" /> {mensajeOk}{" "}
          {!esStaff && <button type="button" className="btn btn-secondary btn-sm" onClick={() => navigate("/mis-turnos")}>Ver mis turnos</button>}
        </p>
      )}
    </main>
  );
}
