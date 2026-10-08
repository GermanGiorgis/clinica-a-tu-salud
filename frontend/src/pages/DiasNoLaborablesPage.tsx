import { useEffect, useState, type FormEvent } from "react";
import { CalendarX, Phone, Trash, UserMinus, Warning } from "@phosphor-icons/react";
import {
  cancelarTurnosAfectados,
  crearDiaNoLaborable,
  eliminarDiaNoLaborable,
  listarDiasNoLaborables,
  turnosAfectados,
} from "../api/diasNoLaborables";
import { listarMedicos } from "../api/medicos";
import { listarMotivosCancelacion } from "../api/motivosCancelacion";
import { extraerError } from "../utils/errores";
import type { DiaNoLaborable, Medico, MotivoCancelacion, TurnoAfectado } from "../types";
import "./DiasNoLaborablesPage.css";

function fechaLarga(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString("es-AR", { weekday: "short", day: "numeric", month: "long", year: "numeric" });
}

function rango(dia: DiaNoLaborable): string {
  return dia.desde === dia.hasta ? fechaLarga(dia.desde) : `${fechaLarga(dia.desde)} al ${fechaLarga(dia.hasta)}`;
}

/**
 * Feriados, cierres y ausencias de médicos, cargados por anticipado. Mientras un período existe no se
 * ofrecen ni se aceptan reservas esos días. Si ya había turnos, no se cancelan solos: se listan con el
 * teléfono del paciente (el sistema no manda avisos) y recién después se cancelan en bloque.
 */
export function DiasNoLaborablesPage() {
  const [dias, setDias] = useState<DiaNoLaborable[] | null>(null);
  const [medicos, setMedicos] = useState<Medico[]>([]);
  const [motivos, setMotivos] = useState<MotivoCancelacion[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [descripcion, setDescripcion] = useState("");
  const [medicoId, setMedicoId] = useState("");
  const [guardando, setGuardando] = useState(false);

  /** Período con sus turnos afectados a la vista (el que se acaba de cargar, o el que se pidió revisar). */
  const [revisando, setRevisando] = useState<{ dia: DiaNoLaborable; turnos: TurnoAfectado[] } | null>(null);
  const [motivoId, setMotivoId] = useState("");
  const [cancelando, setCancelando] = useState(false);

  function cargar() {
    listarDiasNoLaborables()
      .then(setDias)
      .catch((err) => setError(extraerError(err, "No se pudo cargar la lista.")));
  }

  useEffect(() => {
    cargar();
    listarMedicos().then(setMedicos).catch(() => {});
    listarMotivosCancelacion().then(setMotivos).catch(() => {});
  }, []);

  async function revisar(dia: DiaNoLaborable) {
    setError(null);
    try {
      setRevisando({ dia, turnos: await turnosAfectados(dia.id) });
      setMotivoId("");
    } catch (err) {
      setError(extraerError(err, "No se pudieron cargar los turnos afectados."));
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    setAviso(null);
    try {
      const nuevo = await crearDiaNoLaborable({
        desde,
        hasta: hasta || desde,
        descripcion,
        medicoId: medicoId ? Number(medicoId) : undefined,
      });
      setDesde("");
      setHasta("");
      setDescripcion("");
      setMedicoId("");
      cargar();
      const afectados = await turnosAfectados(nuevo.id);
      if (afectados.length > 0) {
        setRevisando({ dia: nuevo, turnos: afectados });
        setMotivoId("");
        setAviso(`Se cargó el período, pero ya hay ${afectados.length} turno${afectados.length === 1 ? "" : "s"} reservado${afectados.length === 1 ? "" : "s"} en esas fechas. No se cancelaron: avisales a los pacientes y después cancelalos.`);
      } else {
        setRevisando(null);
        setAviso("Período cargado. No había turnos reservados en esas fechas.");
      }
    } catch (err) {
      setError(extraerError(err, "No se pudo cargar el período."));
    } finally {
      setGuardando(false);
    }
  }

  async function handleEliminar(dia: DiaNoLaborable) {
    if (!window.confirm(`¿Quitar "${dia.descripcion}"? Esos días vuelven a aceptar reservas.`)) return;
    setError(null);
    setAviso(null);
    try {
      await eliminarDiaNoLaborable(dia.id);
      if (revisando?.dia.id === dia.id) setRevisando(null);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    }
  }

  async function handleCancelarTodos() {
    if (!revisando) return;
    if (!motivoId) {
      setError("Elegí el motivo de la cancelación.");
      return;
    }
    const { dia, turnos } = revisando;
    if (!window.confirm(`¿Cancelar ${turnos.length} turno${turnos.length === 1 ? "" : "s"}? Esta acción no se puede deshacer.`)) return;
    setCancelando(true);
    setError(null);
    setAviso(null);
    try {
      const cancelados = await cancelarTurnosAfectados(dia.id, Number(motivoId));
      setRevisando({ dia, turnos: await turnosAfectados(dia.id) });
      setAviso(`Se cancelaron ${cancelados} turno${cancelados === 1 ? "" : "s"}.`);
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setCancelando(false);
    }
  }

  return (
    <main className="page">
      <h1>Feriados y ausencias</h1>
      <p className="dias__intro">
        Cargalos con anticipación: mientras un período exista, no se ofrecen ni se aceptan reservas esos días.
        Sin médico vale para toda la clínica (feriado, cierre); con un médico, solo para su agenda (licencia,
        enfermedad).
      </p>

      <form className="card dias__form" onSubmit={handleSubmit}>
        <label>
          Desde
          <input type="date" className="input" required value={desde} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label>
          Hasta (si es un solo día, dejalo vacío)
          <input type="date" className="input" min={desde || undefined} value={hasta} onChange={(e) => setHasta(e.target.value)} />
        </label>
        <label className="dias__ancho">
          Descripción
          <input className="input" required maxLength={150} placeholder="Ej.: Día de la Vendimia" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
        </label>
        <label>
          Médico (opcional)
          <select value={medicoId} onChange={(e) => setMedicoId(e.target.value)}>
            <option value="">Toda la clínica</option>
            {medicos.map((m) => (
              <option key={m.id} value={m.id}>{m.nombre} {m.apellido}</option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-primary" disabled={guardando || !desde || !descripcion.trim()}>
          <CalendarX size={18} aria-hidden="true" /> {guardando ? "Guardando…" : "Cargar período"}
        </button>
      </form>

      {error && <p className="error-banner">{error}</p>}
      {aviso && <p className="dias__aviso">{aviso}</p>}

      <h2>Próximos períodos</h2>
      {dias === null && !error && <p className="empty-state">Cargando…</p>}
      {dias !== null && dias.length === 0 && <p className="empty-state">No hay feriados ni ausencias cargados.</p>}
      <ul className="dias__lista">
        {dias?.map((dia) => (
          <li key={dia.id} className="card dias__item">
            <div className="dias__datos">
              <strong>{dia.descripcion}</strong>
              <span>{rango(dia)}</span>
              <span className="dias__alcance">{dia.medicoNombre ? `Solo ${dia.medicoNombre}` : "Toda la clínica"}</span>
            </div>
            <div className="dias__acciones">
              <button type="button" className="btn btn-secondary btn-sm" onClick={() => revisar(dia)}>
                <UserMinus size={16} aria-hidden="true" /> Ver turnos afectados
              </button>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => handleEliminar(dia)} aria-label={`Quitar ${dia.descripcion}`}>
                <Trash size={16} aria-hidden="true" /> Quitar
              </button>
            </div>
          </li>
        ))}
      </ul>

      {revisando && (
        <section className="card dias__afectados" aria-label="Turnos afectados">
          <h2>Turnos afectados por «{revisando.dia.descripcion}»</h2>
          {revisando.turnos.length === 0 ? (
            <p className="empty-state">No hay turnos pendientes en ese período.</p>
          ) : (
            <>
              <p className="dias__advertencia">
                <Warning size={18} aria-hidden="true" /> Llamá a estos pacientes antes de cancelar: el sistema no les avisa.
              </p>
              <ul className="dias__turnos">
                {revisando.turnos.map((t) => (
                  <li key={t.id}>
                    <strong>{t.pacienteNombre}</strong>
                    <span>{new Date(t.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}</span>
                    <span>{t.medicoNombre} · {t.consultorioNombre}</span>
                    <span>
                      <Phone size={14} aria-hidden="true" /> {t.pacienteTelefono ?? "Sin teléfono cargado"}
                    </span>
                  </li>
                ))}
              </ul>
              <div className="dias__cancelar">
                <select aria-label="Motivo de cancelación" value={motivoId} onChange={(e) => setMotivoId(e.target.value)}>
                  <option value="">Motivo de cancelación…</option>
                  {motivos.map((m) => (
                    <option key={m.id} value={m.id}>{m.nombre}</option>
                  ))}
                </select>
                <button type="button" className="btn btn-danger" disabled={cancelando} onClick={handleCancelarTodos}>
                  {cancelando ? "Cancelando…" : `Cancelar los ${revisando.turnos.length} turnos`}
                </button>
              </div>
            </>
          )}
        </section>
      )}
    </main>
  );
}
