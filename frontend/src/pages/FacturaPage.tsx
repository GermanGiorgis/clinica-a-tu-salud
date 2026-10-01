import { useEffect, useState, type FormEvent } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { Plus, Receipt, Trash } from "@phosphor-icons/react";
import { crearFactura, obtenerFactura } from "../api/facturas";
import { extraerError } from "../utils/errores";
import type { Factura, MedioPago, Turno } from "../types";
import "./FacturaPage.css";

const ETIQUETA_MEDIO_PAGO: Record<MedioPago, string> = {
  EFECTIVO: "Efectivo",
  TARJETA: "Tarjeta",
  TRANSFERENCIA: "Transferencia",
  OBRA_SOCIAL: "Obra social",
};

interface ItemForm {
  descripcion: string;
  importe: string;
}

export function FacturaPage() {
  const { turnoId } = useParams<{ turnoId: string }>();
  const id = Number(turnoId);
  const location = useLocation();
  const turnoInfo = (location.state as { turno?: Turno } | null)?.turno;

  const [factura, setFactura] = useState<Factura | null>(null);
  const [existe, setExiste] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const [medioPago, setMedioPago] = useState<MedioPago>("EFECTIVO");
  const [items, setItems] = useState<ItemForm[]>([{ descripcion: "", importe: "" }]);

  function cargar() {
    obtenerFactura(id)
      .then((f) => {
        setFactura(f);
        setExiste(true);
      })
      .catch((err) => {
        if (err?.response?.status === 404) {
          setExiste(false);
          setFactura(null);
        } else {
          setError(extraerError(err));
        }
      });
  }

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  function actualizarItem(i: number, campo: keyof ItemForm, valor: string) {
    setItems(items.map((it, idx) => (idx === i ? { ...it, [campo]: valor } : it)));
  }

  const totalEstimado = items.reduce((acc, it) => acc + (Number(it.importe) || 0), 0);

  async function handleCrear(e: FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await crearFactura(id, {
        medioPago,
        items: items
          .filter((it) => it.descripcion.trim() && it.importe)
          .map((it) => ({ descripcion: it.descripcion, importe: Number(it.importe) })),
      });
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <main className="page">
      <Link to="/agenda" className="volver">← Volver a la agenda</Link>
      <h1>Factura</h1>
      {turnoInfo && (
        <p className="consulta__info">
          {turnoInfo.pacienteNombre} · {new Date(turnoInfo.inicio).toLocaleString("es-AR", { dateStyle: "full", timeStyle: "short" })}
        </p>
      )}

      {error && <p className="error-banner">{error}</p>}
      {existe === null && !error && <p className="empty-state">Cargando…</p>}

      {existe === true && factura && (
        <div className="card factura__detalle">
          <div className="factura__cabecera">
            <span><Receipt size={18} aria-hidden="true" /> {ETIQUETA_MEDIO_PAGO[factura.medioPago]}</span>
            <strong>${factura.total.toFixed(2)}</strong>
          </div>
          <ul>
            {factura.items.map((it) => (
              <li key={it.id}>{it.descripcion} — ${it.importe.toFixed(2)}</li>
            ))}
          </ul>
        </div>
      )}

      {existe === false && (
        <form className="factura__form" onSubmit={handleCrear}>
          <label>
            Medio de pago
            <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
              {Object.entries(ETIQUETA_MEDIO_PAGO).map(([valor, etiqueta]) => (
                <option key={valor} value={valor}>{etiqueta}</option>
              ))}
            </select>
          </label>

          {items.map((it, i) => (
            <div key={i} className="factura__fila">
              <input
                placeholder="Descripción"
                value={it.descripcion}
                onChange={(e) => actualizarItem(i, "descripcion", e.target.value)}
              />
              <input
                placeholder="Importe"
                type="number"
                min={0}
                step="0.01"
                value={it.importe}
                onChange={(e) => actualizarItem(i, "importe", e.target.value)}
              />
              {items.length > 1 && (
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setItems(items.filter((_, idx) => idx !== i))}>
                  <Trash size={16} aria-hidden="true" />
                </button>
              )}
            </div>
          ))}
          <button type="button" className="btn btn-secondary btn-sm factura__agregar" onClick={() => setItems([...items, { descripcion: "", importe: "" }])}>
            <Plus size={16} aria-hidden="true" /> Agregar ítem
          </button>

          <p className="factura__total">Total: ${totalEstimado.toFixed(2)}</p>

          <button type="submit" className="btn btn-primary" disabled={guardando}>
            {guardando ? "Guardando…" : "Emitir factura"}
          </button>
        </form>
      )}
    </main>
  );
}
