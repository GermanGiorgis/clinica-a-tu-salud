import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { MagnifyingGlass, Stethoscope } from "@phosphor-icons/react";
import { listarMedicos } from "../api/medicos";
import { listarPatologias } from "../api/patologias";
import type { Medico, Patologia } from "../types";
import "./MedicosPage.css";

export function MedicosPage() {
  const [medicos, setMedicos] = useState<Medico[] | null>(null);
  const [patologias, setPatologias] = useState<Patologia[]>([]);
  const [patologiaId, setPatologiaId] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listarPatologias().then(setPatologias).catch(() => {});
  }, []);

  useEffect(() => {
    const especialidadId = patologias.find((p) => String(p.id) === patologiaId)?.especialidad.id;
    listarMedicos(especialidadId)
      .then(setMedicos)
      .catch(() => setError("No se pudo cargar la lista de médicos."));
  }, [patologiaId, patologias]);

  return (
    <main className="page">
      <h1>Médicos</h1>
      {patologias.length > 0 && (
        <label className="medicos-lista__filtro">
          <MagnifyingGlass size={16} aria-hidden="true" /> ¿Qué te pasa?
          <select value={patologiaId} onChange={(e) => setPatologiaId(e.target.value)}>
            <option value="">Ver todos los médicos</option>
            {patologias.map((p) => (
              <option key={p.id} value={p.id}>{p.nombre}</option>
            ))}
          </select>
        </label>
      )}
      {error && <p className="error-banner">{error}</p>}
      {!error && medicos === null && <p className="empty-state">Cargando…</p>}
      {medicos !== null && medicos.length === 0 && <p className="empty-state">No hay médicos para esa especialidad.</p>}
      <ul className="medicos-lista">
        {medicos?.map((medico) => (
          <li key={medico.id}>
            <Link to={`/medicos/${medico.id}`} className="card medicos-lista__card">
              <span className="medicos-lista__avatar"><Stethoscope size={22} weight="duotone" aria-hidden="true" /></span>
              <span className="medicos-lista__datos">
                <strong>{medico.nombre} {medico.apellido}</strong>
                <span className="medicos-lista__matricula">Matrícula {medico.matricula}</span>
                <span className="medicos-lista__especialidades">
                  {medico.especialidades.length > 0
                    ? medico.especialidades.map((e) => e.nombre).join(", ")
                    : "Sin especialidad cargada"}
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  );
}
