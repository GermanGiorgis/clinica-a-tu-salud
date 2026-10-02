import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { HistorialEntradas } from "../components/HistorialEntradas";
import { historialDePaciente } from "../api/historial";
import { extraerError } from "../utils/errores";
import type { HistorialEntry } from "../types";

/** Historial de un paciente visto por un médico (que ya lo atendió o tiene turno con él) o por un admin. */
export function HistorialPacientePage() {
  const { pacienteId } = useParams<{ pacienteId: string }>();
  const location = useLocation();
  const pacienteNombre = (location.state as { pacienteNombre?: string } | null)?.pacienteNombre;

  const [historial, setHistorial] = useState<HistorialEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    historialDePaciente(Number(pacienteId))
      .then(setHistorial)
      .catch((err) => setError(extraerError(err, "No se pudo cargar el historial.")));
  }, [pacienteId]);

  return (
    <main className="page">
      <h1>Historial{pacienteNombre ? ` de ${pacienteNombre}` : " del paciente"}</h1>
      <p><Link to="/agenda">← Volver a la agenda</Link></p>
      {error && <p className="error-banner">{error}</p>}
      {!error && historial === null && <p className="empty-state">Cargando…</p>}
      {historial !== null && historial.length === 0 && <p className="empty-state">Este paciente todavía no tiene consultas atendidas.</p>}
      {historial !== null && <HistorialEntradas entradas={historial} />}
    </main>
  );
}
