import { useEffect, useState } from "react";
import { CalendarCheck } from "@phosphor-icons/react";
import { obtenerDisponibilidad } from "../api/medicos";
import { reprogramarTurno } from "../api/turnos";
import { extraerError } from "../utils/errores";
import { fechaLegible, hoyISO, ultimoDiaReservableISO } from "../utils/fechas";
import type { Turno } from "../types";
import "./ReprogramarTurno.css";

interface Props {
  turno: Turno;
  /** Se llama con el turno ya movido. */
  onReprogramado: (turno: Turno) => void;
  onCerrar: () => void;
}

/**
 * Mover un turno a otro horario del mismo médico, sin cancelarlo: así no se pierde el lugar si otra persona se
 * lleva el horario nuevo en el medio. Para cambiar de médico hay que cancelar y reservar de nuevo.
 */
export function ReprogramarTurno({ turno, onReprogramado, onCerrar }: Props) {
  const [fecha, setFecha] = useState(turno.inicio.slice(0, 10) >= hoyISO() ? turno.inicio.slice(0, 10) : hoyISO());
  const [horarios, setHorarios] = useState<string[] | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [elegido, setElegido] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargarHorarios() {
    return obtenerDisponibilidad(turno.medicoId, fecha)
      .then((d) => {
        setHorarios(d.horariosLibres);
        setAviso(d.aviso ?? null);
      })
      .catch(() => setError("No se pudo calcular la disponibilidad."));
  }

  useEffect(() => {
    setHorarios(null);
    setAviso(null);
    setElegido(null);
    cargarHorarios();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fecha, turno.medicoId]);

  async function confirmar() {
    if (!elegido) return;
    setGuardando(true);
    setError(null);
    try {
      onReprogramado(await reprogramarTurno(turno.id, `${fecha}T${elegido}`));
    } catch (err) {
      setError(extraerError(err, "No se pudo reprogramar el turno."));
      // Casi siempre es un 409: otra persona se llevó ese horario. Se refresca para que deje de verse.
      setElegido(null);
      cargarHorarios();
    } finally {
      setGuardando(false);
    }
  }

  return (
    <section className="reprogramar" aria-label="Reprogramar turno">
      <h3>Elegí el nuevo horario con {turno.medicoNombre}</h3>
      <label className="reprogramar__fecha">
        Día
        <input type="date" value={fecha} min={hoyISO()} max={ultimoDiaReservableISO()} onChange={(e) => setFecha(e.target.value)} />
      </label>

      {horarios === null && !error && <p className="empty-state">Buscando horarios…</p>}
      {horarios !== null && horarios.length === 0 && (
        <p className={aviso ? "reprogramar__cerrado" : "empty-state"}>{aviso ?? "No hay horarios libres ese día."}</p>
      )}
      <ul className="reprogramar__horarios">
        {horarios?.map((h) => (
          <li key={h}>
            <button type="button" className={elegido === h ? "activo" : ""} onClick={() => setElegido(h)}>
              {h.slice(0, 5)}
            </button>
          </li>
        ))}
      </ul>

      {error && <p className="error-banner">{error}</p>}
      <div className="reprogramar__acciones">
        <button type="button" className="btn btn-primary btn-sm" disabled={!elegido || guardando} onClick={confirmar}>
          <CalendarCheck size={16} aria-hidden="true" />
          {guardando ? "Moviendo…" : elegido ? `Mover al ${fechaLegible(fecha)}, ${elegido.slice(0, 5)}` : "Elegí un horario"}
        </button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={onCerrar}>No reprogramar</button>
      </div>
      <p className="reprogramar__nota">El turno vuelve a quedar como "Reservado": hay que confirmarlo de nuevo.</p>
    </section>
  );
}
