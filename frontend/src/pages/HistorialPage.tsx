import { useEffect, useState } from "react";
import { miHistorial } from "../api/historial";
import type { HistorialEntry } from "../types";
import "./HistorialPage.css";

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
      {error && <p className="auth-form__error">{error}</p>}
      {!error && historial === null && <p>Cargando…</p>}
      {historial !== null && historial.length === 0 && <p>Todavía no tenés consultas atendidas.</p>}

      <ul className="historial">
        {historial?.map((entrada) => (
          <li key={entrada.turnoId} className="historial__item">
            <header>
              <strong>{entrada.medicoNombre}</strong>
              <span>{new Date(entrada.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}</span>
              <span>{entrada.consultorioNombre}</span>
            </header>

            {entrada.consulta.notas && <p className="historial__notas">{entrada.consulta.notas}</p>}

            {entrada.consulta.diagnosticos.length > 0 && (
              <div>
                <h3>Diagnósticos</h3>
                <ul>
                  {entrada.consulta.diagnosticos.map((d) => (
                    <li key={d.id}>
                      {d.descripcion}
                      {d.codigoCie10 && <span className="historial__cie10"> ({d.codigoCie10})</span>}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {entrada.consulta.recetas.length > 0 && (
              <div>
                <h3>Recetas</h3>
                {entrada.consulta.recetas.map((r) => (
                  <div key={r.id} className="historial__receta">
                    {r.observaciones && <p className="historial__observaciones">{r.observaciones}</p>}
                    <ul>
                      {r.items.map((it) => (
                        <li key={it.id}>
                          {it.medicamentoNombre} — {it.dosis}, {it.frecuencia}, por {it.duracionDias} días
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            )}
          </li>
        ))}
      </ul>
    </main>
  );
}
