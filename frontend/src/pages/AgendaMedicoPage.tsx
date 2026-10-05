import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarBlank, CheckCircle, ClipboardText, ClockCounterClockwise, MapPin, Receipt, ShieldCheck, UserCircleMinus, XCircle } from "@phosphor-icons/react";
import { listarMedicos } from "../api/medicos";
import { listarMotivosCancelacion } from "../api/motivosCancelacion";
import { agendaMedico, cancelarTurno, confirmarTurno, marcarAusente } from "../api/turnos";
import { useAuth } from "../context/AuthContext";
import { extraerError } from "../utils/errores";
import { ETIQUETA_ESTADO } from "../utils/etiquetas";
import type { Medico, MotivoCancelacion, Turno } from "../types";
import "./AgendaMedicoPage.css";

export function AgendaMedicoPage() {
  const { usuario } = useAuth();
  const esAdmin = usuario?.rol === "ADMIN";

  const [medicos, setMedicos] = useState<Medico[]>([]);
  const [medicoId, setMedicoId] = useState<number | null>(null);
  const [turnos, setTurnos] = useState<Turno[] | null>(null);
  const [motivos, setMotivos] = useState<MotivoCancelacion[]>([]);
  const [motivoPorTurno, setMotivoPorTurno] = useState<Record<number, number>>({});
  const [error, setError] = useState<string | null>(null);
  const [procesando, setProcesando] = useState<number | null>(null);
  /** Vacío = los turnos de hoy en adelante; con una fecha, los de ese día (también pasado: para cerrar los que quedaron sin resolver). */
  const [fecha, setFecha] = useState("");

  useEffect(() => {
    listarMotivosCancelacion().then(setMotivos).catch(() => {});
    if (esAdmin) {
      listarMedicos().then(setMedicos).catch(() => setError("No se pudo cargar la lista de médicos."));
    }
  }, [esAdmin]);

  function cargar() {
    if (esAdmin && medicoId === null) {
      setTurnos(null);
      return;
    }
    agendaMedico(medicoId ?? undefined, fecha || undefined)
      .then(setTurnos)
      .catch(() => setError("No se pudo cargar la agenda."));
  }

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [medicoId, esAdmin, fecha]);

  async function handleConfirmar(id: number) {
    setProcesando(id);
    setError(null);
    try {
      await confirmarTurno(id);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setProcesando(null);
    }
  }

  async function handleAusente(id: number) {
    setProcesando(id);
    setError(null);
    try {
      await marcarAusente(id);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setProcesando(null);
    }
  }

  async function handleCancelar(id: number) {
    if (motivos.length === 0) {
      setError("Todavía no hay motivos de cancelación cargados.");
      return;
    }
    // Sin motivo preseleccionado: evita cancelar con uno que nadie eligió.
    const motivoId = motivoPorTurno[id];
    if (!motivoId) {
      setError("Elegí el motivo de la cancelación.");
      return;
    }
    if (!window.confirm("¿Querés cancelar este turno? Esta acción no se puede deshacer.")) {
      return;
    }
    setProcesando(id);
    setError(null);
    try {
      await cancelarTurno(id, motivoId);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setProcesando(null);
    }
  }

  return (
    <main className="page">
      <h1>Agenda</h1>

      <div className="agenda-medico__filtros">
        {esAdmin && (
          <label className="agenda-medico__selector">
            Médico
            <select
              value={medicoId ?? ""}
              onChange={(e) => setMedicoId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">Elegí un médico…</option>
              {medicos.map((m) => (
                <option key={m.id} value={m.id}>{m.nombre} {m.apellido}</option>
              ))}
            </select>
          </label>
        )}
        <label className="agenda-medico__selector">
          Día
          <input type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        </label>
        {fecha && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setFecha("")}>
            Ver los próximos turnos
          </button>
        )}
      </div>

      {error && <p className="error-banner">{error}</p>}
      {esAdmin && medicoId === null && !error && <p className="empty-state">Elegí un médico para ver su agenda.</p>}
      {(!esAdmin || medicoId !== null) && turnos === null && !error && <p className="empty-state">Cargando…</p>}
      {turnos !== null && turnos.length === 0 && (
        <p className="empty-state">{fecha ? "No hay turnos ese día." : "No hay turnos para mostrar."}</p>
      )}

      <ul className="agenda-medico__lista">
        {turnos?.map((turno) => (
          <li key={turno.id} className="card agenda-medico__item">
            <div className="agenda-medico__datos">
              <strong>{turno.pacienteNombre}</strong>
              <span><CalendarBlank size={15} aria-hidden="true" /> {new Date(turno.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}</span>
              <span><MapPin size={15} aria-hidden="true" /> {turno.consultorioNombre}</span>
              <span><ShieldCheck size={15} aria-hidden="true" /> {turno.obraSocialNombre ?? "Particular"}</span>
              {turno.motivoConsulta && <span className="agenda-medico__motivo">"{turno.motivoConsulta}"</span>}
              {turno.motivoCancelacionNombre && (
                <span className="agenda-medico__motivo">Cancelado por: {turno.motivoCancelacionNombre}</span>
              )}
            </div>
            <div className="agenda-medico__acciones">
              <span className={`badge badge--${turno.estado.toLowerCase()}`}>
                {ETIQUETA_ESTADO[turno.estado]}
              </span>
              <Link
                to={`/pacientes/${turno.pacienteId}/historial`}
                state={{ pacienteNombre: turno.pacienteNombre }}
                className="btn btn-ghost btn-sm"
              >
                <ClockCounterClockwise size={16} aria-hidden="true" /> Historial
              </Link>
              {turno.estado === "RESERVADO" && (
                <button type="button" className="btn btn-primary btn-sm" disabled={procesando === turno.id} onClick={() => handleConfirmar(turno.id)}>
                  <CheckCircle size={16} aria-hidden="true" /> Confirmar
                </button>
              )}
              {turno.estado === "CONFIRMADO" && (
                <>
                  <Link to={`/turnos/${turno.id}/consulta`} state={{ turno }} className="btn btn-secondary btn-sm">
                    <ClipboardText size={16} aria-hidden="true" /> Cargar consulta
                  </Link>
                  <button type="button" className="btn btn-ghost btn-sm" disabled={procesando === turno.id} onClick={() => handleAusente(turno.id)}>
                    <UserCircleMinus size={16} aria-hidden="true" /> Marcar ausente
                  </button>
                </>
              )}
              {turno.estado === "ATENDIDO" && (
                <>
                  <Link to={`/turnos/${turno.id}/consulta`} state={{ turno }} className="btn btn-secondary btn-sm">
                    <ClipboardText size={16} aria-hidden="true" /> Ver consulta
                  </Link>
                  <Link to={`/turnos/${turno.id}/factura`} state={{ turno }} className="btn btn-ghost btn-sm">
                    <Receipt size={16} aria-hidden="true" /> Facturar
                  </Link>
                </>
              )}
              {(turno.estado === "RESERVADO" || turno.estado === "CONFIRMADO") && (
                <>
                  <select
                    aria-label="Motivo de cancelación"
                    value={motivoPorTurno[turno.id] ?? ""}
                    onChange={(e) => setMotivoPorTurno({ ...motivoPorTurno, [turno.id]: Number(e.target.value) })}
                  >
                    <option value="">Motivo de cancelación…</option>
                    {motivos.map((m) => (
                      <option key={m.id} value={m.id}>{m.nombre}</option>
                    ))}
                  </select>
                  <button type="button" className="btn btn-danger btn-sm" disabled={procesando === turno.id} onClick={() => handleCancelar(turno.id)}>
                    <XCircle size={16} aria-hidden="true" /> Cancelar
                  </button>
                </>
              )}
            </div>
          </li>
        ))}
      </ul>
    </main>
  );
}
