import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { CalendarBlank, CheckCircle, Stethoscope } from "@phosphor-icons/react";
import { obtenerDisponibilidad, obtenerMedico, obtenerProximoDia } from "../api/medicos";
import { crearTurno } from "../api/turnos";
import { misAfiliaciones } from "../api/afiliaciones";
import { useAuth } from "../context/AuthContext";
import { extraerError } from "../utils/errores";
import { soloDigitos } from "../utils/dni";
import { diaSiguienteISO, fechaLegible, hoyISO, ultimoDiaReservableISO } from "../utils/fechas";
import { esPersonalDeLaClinica } from "../utils/roles";
import type { Afiliacion, Medico } from "../types";
import "./MedicoDetallePage.css";

export function MedicoDetallePage() {
  const { id } = useParams<{ id: string }>();
  const medicoId = Number(id);
  const { usuario, estaAutenticado } = useAuth();
  const navigate = useNavigate();
  // Si se llegó desde una especialidad, "volver" lleva a esa lista y no al inicio de todas.
  const desdeEspecialidad = (useLocation().state as { desdeEspecialidad?: { id: number; nombre?: string } } | null)?.desdeEspecialidad;

  const [medico, setMedico] = useState<Medico | null>(null);
  const [fecha, setFecha] = useState(hoyISO());
  const [horarios, setHorarios] = useState<string[] | null>(null);
  /** Si el día está cerrado (feriado, ausencia del médico), por qué: se muestra en lugar de "no hay horarios". */
  const [aviso, setAviso] = useState<string | null>(null);
  /** Cuando el día elegido no tiene horarios: el primer día siguiente que sí (null = ninguno; undefined = todavía no se sabe). */
  const [proximoDia, setProximoDia] = useState<string | null | undefined>(undefined);
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

  const sinHorarios = horarios !== null && horarios.length === 0;
  useEffect(() => {
    setProximoDia(undefined);
    if (!sinHorarios) return;
    let vigente = true;
    obtenerProximoDia(medicoId, diaSiguienteISO(fecha))
      .then((dia) => vigente && setProximoDia(dia))
      .catch(() => {});
    return () => {
      vigente = false;
    };
  }, [medicoId, fecha, sinHorarios]);

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
      {desdeEspecialidad ? (
        <Link to={`/medicos/especialidad/${desdeEspecialidad.id}`} className="volver">
          ← Volver a {desdeEspecialidad.nombre ?? "la especialidad"}
        </Link>
      ) : (
        <Link to="/medicos" className="volver">← Volver a especialidades</Link>
      )}
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
      {sinHorarios && (
        <p className={aviso ? "medico-detalle__cerrado" : "empty-state"}>{aviso ?? "No hay horarios libres ese día."}</p>
      )}
      {sinHorarios && proximoDia && (
        <button type="button" className="btn btn-secondary medico-detalle__proximo" onClick={() => setFecha(proximoDia)}>
          Ver el próximo día con horarios: {fechaLegible(proximoDia)}
        </button>
      )}
      {sinHorarios && proximoDia === null && !aviso && (
        <p className="empty-state">Este médico no tiene horarios libres en los próximos días.</p>
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
                  <input className="input" inputMode="numeric" autoComplete="off" placeholder="Solo números" value={pacienteDni} onChange={(e) => setPacienteDni(soloDigitos(e.target.value))} />
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
                {reservando ? "Reservando…" : `Reservar el ${fechaLegible(fecha)} a las ${horarioElegido.slice(0, 5)}`}
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
