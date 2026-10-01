import { useEffect, useState } from "react";
import { listarMotivosCancelacion } from "../api/motivosCancelacion";
import { cancelarTurno, confirmarTurno, misTurnos } from "../api/turnos";
import { extraerError } from "../utils/errores";
import type { MotivoCancelacion, Turno } from "../types";
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
      {error && <p className="auth-form__error">{error}</p>}
      {turnos === null && !error && <p>Cargando…</p>}
      {turnos !== null && turnos.length === 0 && <p>Todavía no reservaste ningún turno.</p>}

      <ul className="mis-turnos">
        {turnos?.map((turno) => (
          <li key={turno.id} className="mis-turnos__item">
            <div className="mis-turnos__datos">
              <strong>{turno.medicoNombre}</strong>
              <span>{new Date(turno.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}</span>
              <span>{turno.consultorioNombre}</span>
              {turno.motivoConsulta && <span className="mis-turnos__motivo">"{turno.motivoConsulta}"</span>}
              {turno.motivoCancelacionNombre && (
                <span className="mis-turnos__motivo">Cancelado por: {turno.motivoCancelacionNombre}</span>
              )}
            </div>
            <div className="mis-turnos__acciones">
              <span className={`mis-turnos__estado mis-turnos__estado--${turno.estado.toLowerCase()}`}>
                {ETIQUETA_ESTADO[turno.estado]}
              </span>
              {(turno.estado === "RESERVADO" || turno.estado === "CONFIRMADO") && (
                <>
                  {turno.estado === "RESERVADO" && (
                    <button type="button" disabled={procesando === turno.id} onClick={() => handleConfirmar(turno.id)}>
                      Confirmar
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
