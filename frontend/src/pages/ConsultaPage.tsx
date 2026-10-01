import { useEffect, useState, type FormEvent } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { Pill, Plus, Trash, X } from "@phosphor-icons/react";
import { crearConsulta, obtenerConsultaDeTurno } from "../api/consultas";
import { listarMedicamentos } from "../api/medicamentos";
import { crearReceta } from "../api/recetas";
import { useAuth } from "../context/AuthContext";
import { extraerError } from "../utils/errores";
import type { Consulta, Medicamento, Turno } from "../types";
import "./ConsultaPage.css";

interface DiagnosticoForm {
  descripcion: string;
  codigoCie10: string;
}

interface ItemForm {
  medicamentoId: string;
  dosis: string;
  frecuencia: string;
  duracionDias: string;
}

export function ConsultaPage() {
  const { turnoId } = useParams<{ turnoId: string }>();
  const id = Number(turnoId);
  const location = useLocation();
  const turnoInfo = (location.state as { turno?: Turno } | null)?.turno;
  const { usuario } = useAuth();
  const esStaff = usuario?.rol === "MEDICO" || usuario?.rol === "ADMIN";

  const [consulta, setConsulta] = useState<Consulta | null>(null);
  const [existe, setExiste] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [notas, setNotas] = useState("");
  const [diagnosticos, setDiagnosticos] = useState<DiagnosticoForm[]>([{ descripcion: "", codigoCie10: "" }]);
  const [guardandoConsulta, setGuardandoConsulta] = useState(false);

  const [medicamentos, setMedicamentos] = useState<Medicamento[]>([]);
  const [mostrarFormReceta, setMostrarFormReceta] = useState(false);
  const [observaciones, setObservaciones] = useState("");
  const [items, setItems] = useState<ItemForm[]>([{ medicamentoId: "", dosis: "", frecuencia: "", duracionDias: "" }]);
  const [guardandoReceta, setGuardandoReceta] = useState(false);

  function cargar() {
    obtenerConsultaDeTurno(id)
      .then((c) => {
        setConsulta(c);
        setExiste(true);
      })
      .catch((err) => {
        if (err?.response?.status === 404) {
          setExiste(false);
          setConsulta(null);
        } else {
          setError(extraerError(err));
        }
      });
  }

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    if (esStaff) {
      listarMedicamentos().then(setMedicamentos).catch(() => {});
    }
  }, [esStaff]);

  function actualizarDiagnostico(i: number, campo: keyof DiagnosticoForm, valor: string) {
    setDiagnosticos(diagnosticos.map((d, idx) => (idx === i ? { ...d, [campo]: valor } : d)));
  }

  async function handleCrearConsulta(e: FormEvent) {
    e.preventDefault();
    setGuardandoConsulta(true);
    setError(null);
    try {
      await crearConsulta(id, {
        notas: notas || undefined,
        diagnosticos: diagnosticos
          .filter((d) => d.descripcion.trim())
          .map((d) => ({ descripcion: d.descripcion, codigoCie10: d.codigoCie10 || null })),
      });
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setGuardandoConsulta(false);
    }
  }

  function actualizarItem(i: number, campo: keyof ItemForm, valor: string) {
    setItems(items.map((it, idx) => (idx === i ? { ...it, [campo]: valor } : it)));
  }

  async function handleCrearReceta(e: FormEvent) {
    e.preventDefault();
    if (!consulta) return;
    setGuardandoReceta(true);
    setError(null);
    try {
      await crearReceta(consulta.id, {
        observaciones: observaciones || undefined,
        items: items
          .filter((it) => it.medicamentoId && it.dosis && it.frecuencia && it.duracionDias)
          .map((it) => ({
            medicamentoId: Number(it.medicamentoId),
            dosis: it.dosis,
            frecuencia: it.frecuencia,
            duracionDias: Number(it.duracionDias),
          })),
      });
      setObservaciones("");
      setItems([{ medicamentoId: "", dosis: "", frecuencia: "", duracionDias: "" }]);
      setMostrarFormReceta(false);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setGuardandoReceta(false);
    }
  }

  return (
    <main className="page">
      <Link to="/agenda" className="volver">← Volver a la agenda</Link>
      <h1>Consulta</h1>
      {turnoInfo && (
        <p className="consulta__info">
          {turnoInfo.pacienteNombre} · {new Date(turnoInfo.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}
        </p>
      )}

      {error && <p className="error-banner">{error}</p>}
      {existe === null && !error && <p className="empty-state">Cargando…</p>}

      {existe === false && esStaff && (
        <form className="consulta__form" onSubmit={handleCrearConsulta}>
          <h2>Cargar consulta</h2>
          <label>
            Notas
            <textarea value={notas} onChange={(e) => setNotas(e.target.value)} rows={4} />
          </label>

          <h3>Diagnósticos</h3>
          {diagnosticos.map((d, i) => (
            <div key={i} className="consulta__fila">
              <input
                placeholder="Descripción"
                value={d.descripcion}
                onChange={(e) => actualizarDiagnostico(i, "descripcion", e.target.value)}
              />
              <input
                placeholder="Código CIE-10 (opcional)"
                value={d.codigoCie10}
                onChange={(e) => actualizarDiagnostico(i, "codigoCie10", e.target.value)}
              />
              {diagnosticos.length > 1 && (
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setDiagnosticos(diagnosticos.filter((_, idx) => idx !== i))}>
                  <Trash size={16} aria-hidden="true" />
                </button>
              )}
            </div>
          ))}
          <button type="button" className="btn btn-secondary btn-sm consulta__agregar" onClick={() => setDiagnosticos([...diagnosticos, { descripcion: "", codigoCie10: "" }])}>
            <Plus size={16} aria-hidden="true" /> Agregar diagnóstico
          </button>

          <button type="submit" className="btn btn-primary consulta__guardar" disabled={guardandoConsulta}>
            {guardandoConsulta ? "Guardando…" : "Guardar consulta"}
          </button>
        </form>
      )}

      {existe === false && !esStaff && <p className="empty-state">Esta consulta todavía no fue cargada.</p>}

      {existe === true && consulta && (
        <div className="consulta__detalle">
          {consulta.notas && (
            <section>
              <h2>Notas</h2>
              <p>{consulta.notas}</p>
            </section>
          )}

          <section>
            <h2>Diagnósticos</h2>
            <ul>
              {consulta.diagnosticos.map((d) => (
                <li key={d.id}>
                  {d.descripcion}
                  {d.codigoCie10 && <span className="consulta__cie10"> ({d.codigoCie10})</span>}
                </li>
              ))}
            </ul>
          </section>

          <section>
            <h2>Recetas</h2>
            {consulta.recetas.length === 0 && <p className="empty-state">No se emitieron recetas.</p>}
            {consulta.recetas.map((r) => (
              <div key={r.id} className="consulta__receta">
                {r.observaciones && <p className="consulta__observaciones">{r.observaciones}</p>}
                <ul>
                  {r.items.map((it) => (
                    <li key={it.id}>
                      <Pill size={16} aria-hidden="true" /> {it.medicamentoNombre} — {it.dosis}, {it.frecuencia}, por {it.duracionDias} días
                    </li>
                  ))}
                </ul>
              </div>
            ))}

            {esStaff && !mostrarFormReceta && (
              <button type="button" className="btn btn-secondary btn-sm" onClick={() => setMostrarFormReceta(true)}>
                <Plus size={16} aria-hidden="true" /> Agregar receta
              </button>
            )}

            {esStaff && mostrarFormReceta && (
              <form className="consulta__form" onSubmit={handleCrearReceta}>
                <label>
                  Observaciones (opcional)
                  <input value={observaciones} onChange={(e) => setObservaciones(e.target.value)} />
                </label>
                {items.map((it, i) => (
                  <div key={i} className="consulta__fila">
                    <select value={it.medicamentoId} onChange={(e) => actualizarItem(i, "medicamentoId", e.target.value)}>
                      <option value="">Medicamento…</option>
                      {medicamentos.map((m) => (
                        <option key={m.id} value={m.id}>{m.nombre}</option>
                      ))}
                    </select>
                    <input placeholder="Dosis" value={it.dosis} onChange={(e) => actualizarItem(i, "dosis", e.target.value)} />
                    <input placeholder="Frecuencia" value={it.frecuencia} onChange={(e) => actualizarItem(i, "frecuencia", e.target.value)} />
                    <input
                      placeholder="Días"
                      type="number"
                      min={1}
                      value={it.duracionDias}
                      onChange={(e) => actualizarItem(i, "duracionDias", e.target.value)}
                    />
                    {items.length > 1 && (
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setItems(items.filter((_, idx) => idx !== i))}>
                        <Trash size={16} aria-hidden="true" />
                      </button>
                    )}
                  </div>
                ))}
                <button
                  type="button"
                  className="btn btn-secondary btn-sm consulta__agregar"
                  onClick={() => setItems([...items, { medicamentoId: "", dosis: "", frecuencia: "", duracionDias: "" }])}
                >
                  <Plus size={16} aria-hidden="true" /> Agregar medicamento
                </button>
                <div className="consulta__fila">
                  <button type="submit" className="btn btn-primary btn-sm" disabled={guardandoReceta}>{guardandoReceta ? "Guardando…" : "Guardar receta"}</button>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => setMostrarFormReceta(false)}>
                    <X size={16} aria-hidden="true" /> Cancelar
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      )}
    </main>
  );
}
