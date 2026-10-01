import { useEffect, useState } from "react";
import { CalendarBlank, CheckCircle, MapPin, ShieldCheck, Star, XCircle } from "@phosphor-icons/react";
import { listarMotivosCancelacion } from "../api/motivosCancelacion";
import { cancelarTurno, confirmarTurno, misTurnos } from "../api/turnos";
import { crearEncuesta, obtenerEncuesta } from "../api/encuestas";
import { extraerError } from "../utils/errores";
import type { Encuesta, MotivoCancelacion, Turno } from "../types";
import "./MisTurnosPage.css";

const ETIQUETA_ESTADO: Record<Turno["estado"], string> = {
  RESERVADO: "Reservado",
  CONFIRMADO: "Confirmado",
  ATENDIDO: "Atendido",
  AUSENTE: "Ausente",
  CANCELADO: "Cancelado",
};

export function MisTurnosPage() {
  const [turnos, setTurnos] = useState<Turno[] | null>(null);
  const [motivos, setMotivos] = useState<MotivoCancelacion[]>([]);
  const [motivoPorTurno, setMotivoPorTurno] = useState<Record<number, number>>({});
  const [error, setError] = useState<string | null>(null);
  const [procesando, setProcesando] = useState<number | null>(null);

  function cargar() {
    misTurnos().then(setTurnos).catch(() => setError("No se pudieron cargar tus turnos."));
  }

  useEffect(() => {
    cargar();
    listarMotivosCancelacion().then(setMotivos).catch(() => {});
  }, []);

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

  async function handleCancelar(id: number) {
    const motivoId = motivoPorTurno[id] ?? motivos[0]?.id;
    if (!motivoId) {
      setError("Todavía no hay motivos de cancelación cargados.");
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
      <h1>Mis turnos</h1>
      {error && <p className="error-banner">{error}</p>}
      {turnos === null && !error && <p className="empty-state">Cargando…</p>}
      {turnos !== null && turnos.length === 0 && <p className="empty-state">Todavía no reservaste ningún turno.</p>}

      <ul className="mis-turnos">
        {turnos?.map((turno) => (
          <li key={turno.id} className="card mis-turnos__item">
            <div className="mis-turnos__datos">
              <strong>{turno.medicoNombre}</strong>
              <span><CalendarBlank size={15} aria-hidden="true" /> {new Date(turno.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}</span>
              <span><MapPin size={15} aria-hidden="true" /> {turno.consultorioNombre}</span>
              <span><ShieldCheck size={15} aria-hidden="true" /> {turno.obraSocialNombre ?? "Particular"}</span>
              {turno.motivoConsulta && <span className="mis-turnos__motivo">"{turno.motivoConsulta}"</span>}
              {turno.motivoCancelacionNombre && (
                <span className="mis-turnos__motivo">Cancelado por: {turno.motivoCancelacionNombre}</span>
              )}
            </div>
            <div className="mis-turnos__acciones">
              <span className={`badge badge--${turno.estado.toLowerCase()}`}>
                {ETIQUETA_ESTADO[turno.estado]}
              </span>
              {(turno.estado === "RESERVADO" || turno.estado === "CONFIRMADO") && (
                <>
                  {turno.estado === "RESERVADO" && (
                    <button type="button" className="btn btn-primary btn-sm" disabled={procesando === turno.id} onClick={() => handleConfirmar(turno.id)}>
                      <CheckCircle size={16} aria-hidden="true" /> Confirmar
                    </button>
                  )}
                  <select
                    value={motivoPorTurno[turno.id] ?? motivos[0]?.id ?? ""}
                    onChange={(e) => setMotivoPorTurno({ ...motivoPorTurno, [turno.id]: Number(e.target.value) })}
                  >
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
            {turno.estado === "ATENDIDO" && <EncuestaTurno turnoId={turno.id} />}
          </li>
        ))}
      </ul>
    </main>
  );
}

function EncuestaTurno({ turnoId }: { turnoId: number }) {
  const [encuesta, setEncuesta] = useState<Encuesta | null | undefined>(undefined);
  const [puntaje, setPuntaje] = useState(5);
  const [comentario, setComentario] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    obtenerEncuesta(turnoId)
      .then(setEncuesta)
      .catch((err) => setEncuesta(err?.response?.status === 404 ? null : null));
  }, [turnoId]);

  async function enviar() {
    setEnviando(true);
    setError(null);
    try {
      const nueva = await crearEncuesta(turnoId, puntaje, comentario || undefined);
      setEncuesta(nueva);
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setEnviando(false);
    }
  }

  if (encuesta === undefined) return null;

  if (encuesta) {
    return (
      <div className="mis-turnos__encuesta mis-turnos__encuesta--hecha">
        {Array.from({ length: 5 }, (_, i) => (
          <Star key={i} size={16} weight={i < encuesta.puntaje ? "fill" : "regular"} aria-hidden="true" />
        ))}
        {encuesta.comentario && <span className="mis-turnos__motivo">"{encuesta.comentario}"</span>}
      </div>
    );
  }

  return (
    <div className="mis-turnos__encuesta">
      <span>¿Cómo fue la atención?</span>
      <div className="mis-turnos__estrellas">
        {Array.from({ length: 5 }, (_, i) => (
          <button key={i} type="button" onClick={() => setPuntaje(i + 1)} aria-label={`${i + 1} estrellas`}>
            <Star size={18} weight={i < puntaje ? "fill" : "regular"} aria-hidden="true" />
          </button>
        ))}
      </div>
      <input
        placeholder="Comentario (opcional)"
        value={comentario}
        onChange={(e) => setComentario(e.target.value)}
      />
      <button type="button" className="btn btn-secondary btn-sm" disabled={enviando} onClick={enviar}>
        {enviando ? "Enviando…" : "Enviar"}
      </button>
      {error && <p className="error-banner">{error}</p>}
    </div>
  );
}
