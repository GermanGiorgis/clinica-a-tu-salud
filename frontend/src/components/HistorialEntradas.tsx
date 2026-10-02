import { CalendarBlank, Flask, MapPin, Pill, Stethoscope } from "@phosphor-icons/react";
import type { HistorialEntry } from "../types";
import "../pages/HistorialPage.css";

/** Lista de consultas atendidas; la usan el historial propio del paciente y el que ve un médico/admin. */
export function HistorialEntradas({ entradas }: { entradas: HistorialEntry[] }) {
  return (
    <ul className="historial">
      {entradas.map((entrada) => (
        <li key={entrada.turnoId} className="card historial__item">
          <header>
            <strong><Stethoscope size={16} aria-hidden="true" /> {entrada.medicoNombre}</strong>
            <span><CalendarBlank size={15} aria-hidden="true" /> {new Date(entrada.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}</span>
            <span><MapPin size={15} aria-hidden="true" /> {entrada.consultorioNombre}</span>
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
                        <Pill size={15} aria-hidden="true" /> {it.medicamentoNombre} — {it.dosis}, {it.frecuencia}, por {it.duracionDias} días
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}

          {entrada.consulta.ordenesEstudio.length > 0 && (
            <div>
              <h3>Estudios</h3>
              <ul>
                {entrada.consulta.ordenesEstudio.map((orden) => (
                  <li key={orden.id}>
                    <Flask size={15} aria-hidden="true" /> {orden.estudioNombre}
                    {orden.resultado ? `: ${orden.resultado.resultado}` : " (pendiente)"}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
