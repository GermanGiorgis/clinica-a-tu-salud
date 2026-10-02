import { useEffect, useState } from "react";
import { CalendarCheck, CurrencyDollar, Star, UserCircleMinus, UsersThree } from "@phosphor-icons/react";
import { obtenerMetricas } from "../api/metricas";
import { ETIQUETA_ESTADO, ETIQUETA_MEDIO_PAGO, formatearMoneda } from "../utils/etiquetas";
import { extraerError } from "../utils/errores";
import type { Metricas } from "../types";
import "./MetricasPage.css";

function formatearDecimal(valor: number | null, sufijo = ""): string {
  return valor === null ? "—" : `${valor.toLocaleString("es-AR", { maximumFractionDigits: 2 })}${sufijo}`;
}

export function MetricasPage() {
  const [metricas, setMetricas] = useState<Metricas | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    obtenerMetricas()
      .then(setMetricas)
      .catch((err) => setError(extraerError(err, "No se pudieron cargar las métricas.")));
  }, []);

  if (error) {
    return (
      <main className="page">
        <h1>Métricas</h1>
        <p className="error-banner">{error}</p>
      </main>
    );
  }
  if (metricas === null) {
    return (
      <main className="page">
        <h1>Métricas</h1>
        <p className="empty-state">Cargando…</p>
      </main>
    );
  }

  const maxPorEstado = Math.max(1, ...metricas.turnosPorEstado.map((c) => c.cantidad));
  const maxPorMedioPago = Math.max(1, ...metricas.facturacionPorMedioPago.map((m) => m.total));

  return (
    <main className="page">
      <h1>Métricas</h1>
      <p className="metricas__intro">Resumen de la actividad de toda la clínica.</p>

      <section className="metricas__kpis" aria-label="Indicadores generales">
        <div className="card metricas__kpi">
          <CalendarCheck size={22} aria-hidden="true" />
          <strong>{metricas.totalTurnos}</strong>
          <span>Turnos en total</span>
        </div>
        <div className="card metricas__kpi">
          <UsersThree size={22} aria-hidden="true" />
          <strong>{metricas.pacientesAtendidos}</strong>
          <span>Pacientes atendidos</span>
        </div>
        <div className="card metricas__kpi">
          <UserCircleMinus size={22} aria-hidden="true" />
          <strong>{formatearDecimal(metricas.tasaAusentismo, "%")}</strong>
          <span>Ausentismo</span>
        </div>
        <div className="card metricas__kpi">
          <Star size={22} aria-hidden="true" />
          <strong>{formatearDecimal(metricas.puntajePromedio)}</strong>
          <span>Satisfacción ({metricas.totalEncuestas} {metricas.totalEncuestas === 1 ? "encuesta" : "encuestas"})</span>
        </div>
        <div className="card metricas__kpi">
          <CurrencyDollar size={22} aria-hidden="true" />
          <strong>{formatearMoneda(metricas.facturacionTotal)}</strong>
          <span>Facturado</span>
        </div>
      </section>

      <div className="metricas__paneles">
        <section className="card" aria-labelledby="metricas-estados">
          <h2 id="metricas-estados">Turnos por estado</h2>
          <ul className="metricas__barras">
            {metricas.turnosPorEstado.map((c) => (
              <li key={c.estado}>
                <span className="metricas__etiqueta">{ETIQUETA_ESTADO[c.estado]}</span>
                <span className="metricas__pista" aria-hidden="true">
                  <span
                    className={`metricas__barra metricas__barra--${c.estado.toLowerCase()}`}
                    style={{ width: `${(c.cantidad / maxPorEstado) * 100}%` }}
                  />
                </span>
                <span className="metricas__valor">{c.cantidad}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="card" aria-labelledby="metricas-pagos">
          <h2 id="metricas-pagos">Facturación por medio de pago</h2>
          <ul className="metricas__barras">
            {metricas.facturacionPorMedioPago.map((m) => (
              <li key={m.medioPago}>
                <span className="metricas__etiqueta">{ETIQUETA_MEDIO_PAGO[m.medioPago]}</span>
                <span className="metricas__pista" aria-hidden="true">
                  <span className="metricas__barra" style={{ width: `${(m.total / maxPorMedioPago) * 100}%` }} />
                </span>
                <span className="metricas__valor">{formatearMoneda(m.total)}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="card metricas__medicos" aria-labelledby="metricas-medicos">
        <h2 id="metricas-medicos">Por médico</h2>
        {metricas.medicos.length === 0 ? (
          <p className="empty-state">Todavía no hay médicos cargados.</p>
        ) : (
          <div className="metricas__tabla-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Médico</th>
                  <th scope="col">Atendidos</th>
                  <th scope="col">Ausentes</th>
                  <th scope="col">Satisfacción</th>
                  <th scope="col">Facturado</th>
                </tr>
              </thead>
              <tbody>
                {metricas.medicos.map((m) => (
                  <tr key={m.medicoId}>
                    <th scope="row">{m.nombre}</th>
                    <td>{m.turnosAtendidos}</td>
                    <td>{m.turnosAusentes}</td>
                    <td>{m.puntajePromedio === null ? "—" : `${formatearDecimal(m.puntajePromedio)} (${m.encuestas})`}</td>
                    <td>{formatearMoneda(m.facturado)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
