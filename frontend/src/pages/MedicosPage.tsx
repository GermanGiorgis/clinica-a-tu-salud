import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { listarEspecialidades } from "../api/especialidades";
import { listarMedicos } from "../api/medicos";
import { listarPatologias } from "../api/patologias";
import { iconoDeEspecialidad, normalizar } from "../utils/especialidades";
import type { Especialidad, Medico, Patologia } from "../types";
import "./MedicosPage.css";

/**
 * Punto de partida para sacar un turno: se elige la especialidad (traumatología, dermatología...) y
 * recién ahí se ven los médicos que la atienden. Quien no sabe a cuál ir puede decir qué le pasa y el
 * sistema lo lleva a la especialidad que corresponde.
 */
export function MedicosPage() {
  const navigate = useNavigate();
  const [especialidades, setEspecialidades] = useState<Especialidad[] | null>(null);
  const [medicos, setMedicos] = useState<Medico[] | null>(null);
  const [patologias, setPatologias] = useState<Patologia[]>([]);
  const [busqueda, setBusqueda] = useState("");
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

  const visibles = useMemo(() => {
    const termino = normalizar(busqueda);
    return (especialidades ?? [])
      .filter((e) => normalizar(e.nombre).includes(termino))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  }, [especialidades, busqueda]);

  function handlePatologia(id: string) {
    const patologia = patologias.find((p) => String(p.id) === id);
    if (patologia) {
      navigate(`/medicos/especialidad/${patologia.especialidad.id}`);
    }
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
            onChange={(e) => setBusqueda(e.target.value)}
          />
        </label>
        {patologias.length > 0 && (
          <label className="especialidades__buscador">
            ¿No sabés cuál? ¿Qué te pasa?
            <select value="" onChange={(e) => handlePatologia(e.target.value)}>
              <option value="">Elegí un síntoma…</option>
              {patologias.map((p) => (
                <option key={p.id} value={p.id}>{p.nombre}</option>
              ))}
            </select>
          </label>
        )}
      </div>

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
