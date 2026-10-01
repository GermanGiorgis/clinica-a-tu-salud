import { useEffect, useState, type FormEvent } from "react";
import { Plus, ShieldCheck, Trash } from "@phosphor-icons/react";
import { crearAfiliacion, eliminarAfiliacion, misAfiliaciones } from "../api/afiliaciones";
import { listarObrasSociales } from "../api/obrasSociales";
import { extraerError } from "../utils/errores";
import type { Afiliacion, ObraSocial } from "../types";
import "./MisAfiliacionesPage.css";

function hoyISO(): string {
  return new Date().toLocaleDateString("sv-SE");
}

export function MisAfiliacionesPage() {
  const [afiliaciones, setAfiliaciones] = useState<Afiliacion[] | null>(null);
  const [obrasSociales, setObrasSociales] = useState<ObraSocial[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [eliminando, setEliminando] = useState<number | null>(null);

  const [obraSocialId, setObraSocialId] = useState("");
  const [numeroAfiliado, setNumeroAfiliado] = useState("");
  const [plan, setPlan] = useState("");
  const [vigenteDesde, setVigenteDesde] = useState(hoyISO());
  const [vigenteHasta, setVigenteHasta] = useState("");

  function cargar() {
    misAfiliaciones().then(setAfiliaciones).catch(() => setError("No se pudieron cargar tus afiliaciones."));
  }

  useEffect(() => {
    cargar();
    listarObrasSociales().then(setObrasSociales).catch(() => {});
  }, []);

  async function handleCrear(e: FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await crearAfiliacion({
        obraSocialId: Number(obraSocialId),
        numeroAfiliado,
        plan: plan || undefined,
        vigenteDesde,
        vigenteHasta: vigenteHasta || undefined,
      });
      setObraSocialId("");
      setNumeroAfiliado("");
      setPlan("");
      setVigenteHasta("");
      setMostrarForm(false);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setGuardando(false);
    }
  }

  async function handleEliminar(id: number) {
    setEliminando(id);
    setError(null);
    try {
      await eliminarAfiliacion(id);
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setEliminando(null);
    }
  }

  return (
    <main className="page">
      <h1>Mi cobertura</h1>
      {error && <p className="error-banner">{error}</p>}
      {!error && afiliaciones === null && <p className="empty-state">Cargando…</p>}
      {afiliaciones !== null && afiliaciones.length === 0 && !mostrarForm && (
        <p className="empty-state">Todavía no cargaste ninguna obra social.</p>
      )}

      <ul className="afiliaciones">
        {afiliaciones?.map((a) => (
          <li key={a.id} className="card afiliaciones__item">
            <div>
              <strong><ShieldCheck size={16} aria-hidden="true" /> {a.obraSocialNombre}</strong>
              <span className="afiliaciones__detalle">
                N° {a.numeroAfiliado}{a.plan && ` · ${a.plan}`}
              </span>
              <span className="afiliaciones__vigencia">
                Desde {a.vigenteDesde}{a.vigenteHasta ? ` hasta ${a.vigenteHasta}` : ""}
              </span>
            </div>
            <button type="button" className="btn btn-ghost btn-sm" disabled={eliminando === a.id} onClick={() => handleEliminar(a.id)}>
              <Trash size={16} aria-hidden="true" />
            </button>
          </li>
        ))}
      </ul>

      {!mostrarForm && (
        <button type="button" className="btn btn-secondary" onClick={() => setMostrarForm(true)}>
          <Plus size={16} aria-hidden="true" /> Agregar obra social
        </button>
      )}

      {mostrarForm && (
        <form className="afiliaciones__form" onSubmit={handleCrear}>
          <label>
            Obra social
            <select value={obraSocialId} onChange={(e) => setObraSocialId(e.target.value)} required>
              <option value="">Elegí una obra social…</option>
              {obrasSociales.map((os) => (
                <option key={os.id} value={os.id}>{os.nombre}</option>
              ))}
            </select>
          </label>
          <label>
            Número de afiliado
            <input value={numeroAfiliado} onChange={(e) => setNumeroAfiliado(e.target.value)} required />
          </label>
          <label>
            Plan (opcional)
            <input value={plan} onChange={(e) => setPlan(e.target.value)} />
          </label>
          <div className="auth-form__fila">
            <label>
              Vigente desde
              <input type="date" value={vigenteDesde} onChange={(e) => setVigenteDesde(e.target.value)} required />
            </label>
            <label>
              Vigente hasta (opcional)
              <input type="date" value={vigenteHasta} onChange={(e) => setVigenteHasta(e.target.value)} min={vigenteDesde} />
            </label>
          </div>
          <div className="afiliaciones__acciones-form">
            <button type="submit" className="btn btn-primary" disabled={guardando}>
              {guardando ? "Guardando…" : "Guardar"}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setMostrarForm(false)}>Cancelar</button>
          </div>
        </form>
      )}
    </main>
  );
}
