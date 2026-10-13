import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listarEspecialidades } from "../api/especialidades";
import { listarMedicos } from "../api/medicos";
import { listarPatologias } from "../api/patologias";
import { iconoDeEspecialidad, normalizar } from "../utils/especialidades";
import type { Especialidad, Medico, Patologia } from "../types";
import "./MedicosPage.css";

/**
 * Punto de partida para sacar un turno: se elige la especialidad (traumatología, dermatología...) y
 * recién ahí se ven los médicos que la atienden. Quien no sabe a cuál ir puede decir qué le pasa y se
 * muestran solo las especialidades que atienden ese síntoma (suelen ser dos o tres: "dolor de cabeza" es de
 * neurología o de clínica médica).
 */
export function MedicosPage() {
  const [especialidades, setEspecialidades] = useState<Especialidad[] | null>(null);
  const [medicos, setMedicos] = useState<Medico[] | null>(null);
  const [patologias, setPatologias] = useState<Patologia[]>([]);
  const [busqueda, setBusqueda] = useState("");
  const [sintomaId, setSintomaId] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listarEspecialidades()
      .then(setEspecialidades)
      .catch(() => setError("No se pudo cargar la lista de especialidades."));
    // Sin los médicos igual se puede elegir especialidad; solo faltan los conteos.
    listarMedicos().then(setMedicos).catch(() => {});
    listarPatologias().then(setPatologias).catch(() => {});
  }, []);

  const medicosPorEspecialidad = useMemo(() => {
    const cuenta = new Map<number, number>();
    for (const medico of medicos ?? []) {
      for (const e of medico.especialidades) {
        cuenta.set(e.id, (cuenta.get(e.id) ?? 0) + 1);
      }
    }
    return cuenta;
  }, [medicos]);

  const sintomas = useMemo(
    () => [...patologias].sort((a, b) => a.nombre.localeCompare(b.nombre, "es")),
    [patologias],
  );
  const sintoma = patologias.find((p) => String(p.id) === sintomaId);

  const visibles = useMemo(() => {
    const termino = normalizar(busqueda);
    const delSintoma = sintoma ? new Set(sintoma.especialidades.map((e) => e.id)) : null;
    return (especialidades ?? [])
      .filter((e) => normalizar(e.nombre).includes(termino) && (!delSintoma || delSintoma.has(e.id)))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  }, [especialidades, busqueda, sintoma]);

  // Los dos filtros no se combinan: quien elige un síntoma deja de buscar por nombre, y al revés.
  function elegirSintoma(id: string) {
    setSintomaId(id);
    if (id) setBusqueda("");
  }

  function buscar(texto: string) {
    setBusqueda(texto);
    if (texto) setSintomaId("");
  }

  return (
    <main className="page">
      <h1>Especialidades</h1>
      <p className="especialidades__intro">
        Elegí la especialidad que necesitás y te mostramos los médicos disponibles.
      </p>

      <div className="especialidades__herramientas">
        <label className="especialidades__buscador">
          Buscar especialidad
          <input
            type="search"
            placeholder="Ej.: traumatología"
            value={busqueda}
            onChange={(e) => buscar(e.target.value)}
          />
        </label>
        {patologias.length > 0 && (
          <label className="especialidades__buscador">
            ¿No sabés cuál? ¿Qué te pasa?
            <select value={sintomaId} onChange={(e) => elegirSintoma(e.target.value)}>
              <option value="">Elegí un síntoma…</option>
              {sintomas.map((p) => (
                <option key={p.id} value={p.id}>{p.nombre}</option>
              ))}
            </select>
          </label>
        )}
      </div>

      {sintoma && (
        <div className="especialidades__sugerencia" role="status">
          <p>
            Para <strong>{sintoma.nombre.toLowerCase()}</strong> te sugerimos
            {sintoma.especialidades.length === 1 ? " esta especialidad" : " estas especialidades"}. Elegí la que prefieras
            y mirá sus médicos.
          </p>
          <button type="button" className="btn btn-secondary" onClick={() => elegirSintoma("")}>Ver todas las especialidades</button>
        </div>
      )}
      {patologias.length > 0 && (
        <p className="especialidades__urgencia">
          Si es una urgencia (dolor fuerte en el pecho, falta de aire, desmayo), no esperes un turno: llamá al 107 o andá a una guardia.
        </p>
      )}

      {error && <p className="error-banner">{error}</p>}
      {!error && especialidades === null && <p className="empty-state">Cargando…</p>}
      {especialidades !== null && especialidades.length === 0 && (
        <p className="empty-state">Todavía no hay especialidades cargadas.</p>
      )}
      {especialidades !== null && especialidades.length > 0 && visibles.length === 0 && (
        <p className="empty-state">No encontramos ninguna especialidad con ese nombre.</p>
      )}

      <ul className="especialidades">
        {visibles.map((especialidad) => {
          const Icono = iconoDeEspecialidad(especialidad.nombre);
          const cantidad = medicosPorEspecialidad.get(especialidad.id) ?? 0;
          return (
            <li key={especialidad.id}>
              <Link to={`/medicos/especialidad/${especialidad.id}`} className="card especialidades__card">
                <span className="especialidades__icono"><Icono size={26} weight="duotone" aria-hidden="true" /></span>
                <strong>{especialidad.nombre}</strong>
                {especialidad.descripcion && <span className="especialidades__detalle">{especialidad.descripcion}</span>}
                {medicos !== null && (
                  <span className={cantidad > 0 ? "especialidades__cuenta" : "especialidades__cuenta especialidades__cuenta--vacia"}>
                    {cantidad === 0 ? "Sin médicos por ahora" : cantidad === 1 ? "1 médico" : `${cantidad} médicos`}
                  </span>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </main>
  );
}
