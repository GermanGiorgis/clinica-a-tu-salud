import { useEffect, useState } from "react";
import { listarMedicos } from "../api/medicos";
import { listarMotivosCancelacion } from "../api/motivosCancelacion";
import { agendaMedico, cancelarTurno, confirmarTurno, marcarAusente } from "../api/turnos";
import { useAuth } from "../context/AuthContext";
import { extraerError } from "../utils/errores";
import type { Medico, MotivoCancelacion, Turno } from "../types";
import "./AgendaMedicoPage.css";

const ETIQUETA_ESTADO: Record<Turno["estado"], string> = {
  RESERVADO: "Reservado",
  CONFIRMADO: "Confirmado",
  ATENDIDO: "Atendido",
  AUSENTE: "Ausente",
  CANCELADO: "Cancelado",
};

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
    agendaMedico(medicoId ?? undefined)
      .then(setTurnos)
      .catch(() => setError("No se pudo cargar la agenda."));
  }

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [medicoId, esAdmin]);

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
      <h1>Agenda</h1>

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

      {error && <p className="auth-form__error">{error}</p>}
      {esAdmin && medicoId === null && !error && <p>Elegí un médico para ver su agenda.</p>}
      {(!esAdmin || medicoId !== null) && turnos === null && !error && <p>Cargando…</p>}
      {turnos !== null && turnos.length === 0 && <p>No hay turnos para mostrar.</p>}

      <ul className="agenda-medico__lista">
        {turnos?.map((turno) => (
          <li key={turno.id} className="agenda-medico__item">
            <div className="agenda-medico__datos">
              <strong>{turno.pacienteNombre}</strong>
              <span>{new Date(turno.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}</span>
              <span>{turno.consultorioNombre}</span>
              {turno.motivoConsulta && <span className="agenda-medico__motivo">"{turno.motivoConsulta}"</span>}
              {turno.motivoCancelacionNombre && (
                <span className="agenda-medico__motivo">Cancelado por: {turno.motivoCancelacionNombre}</span>
              )}
            </div>
            <div className="agenda-medico__acciones">
              <span className={`agenda-medico__estado agenda-medico__estado--${turno.estado.toLowerCase()}`}>
                {ETIQUETA_ESTADO[turno.estado]}
              </span>
              {turno.estado === "RESERVADO" && (
                <button type="button" disabled={procesando === turno.id} onClick={() => handleConfirmar(turno.id)}>
                  Confirmar
                </button>
              )}
              {turno.estado === "CONFIRMADO" && (
                <button type="button" disabled={procesando === turno.id} onClick={() => handleAusente(turno.id)}>
                  Marcar ausente
                </button>
              )}
              {(turno.estado === "RESERVADO" || turno.estado === "CONFIRMADO") && (
                <>
                  <select
                    value={motivoPorTurno[turno.id] ?? motivos[0]?.id ?? ""}
                    onChange={(e) => setMotivoPorTurno({ ...motivoPorTurno, [turno.id]: Number(e.target.value) })}
                  >
                    {motivos.map((m) => (
                      <option key={m.id} value={m.id}>{m.nombre}</option>
                    ))}
                  </select>
                  <button type="button" disabled={procesando === turno.id} onClick={() => handleCancelar(turno.id)}>
                    Cancelar
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
