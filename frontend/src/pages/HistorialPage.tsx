import { useEffect, useState } from "react";
import { HistorialEntradas } from "../components/HistorialEntradas";
import { miHistorial } from "../api/historial";
import type { HistorialEntry } from "../types";

export function HistorialPage() {
  const [historial, setHistorial] = useState<HistorialEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    miHistorial()
      .then(setHistorial)
      .catch(() => setError("No se pudo cargar tu historial."));
  }, []);

  return (
    <main className="page">
      <h1>Mi historial</h1>
      {error && <p className="error-banner">{error}</p>}
      {!error && historial === null && <p className="empty-state">Cargando…</p>}
      {historial !== null && historial.length === 0 && <p className="empty-state">Todavía no tenés consultas atendidas.</p>}
      {historial !== null && <HistorialEntradas entradas={historial} />}
    </main>
  );
}
