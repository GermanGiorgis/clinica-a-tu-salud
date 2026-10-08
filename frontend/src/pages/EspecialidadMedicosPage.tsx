import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Stethoscope } from "@phosphor-icons/react";
import { listarEspecialidades } from "../api/especialidades";
import { listarMedicos } from "../api/medicos";
import { iconoDeEspecialidad } from "../utils/especialidades";
import type { Especialidad, Medico } from "../types";
import "./MedicosPage.css";

/** Los médicos de una especialidad: lo que se ve al abrir una de las tarjetas de /medicos. */
export function EspecialidadMedicosPage() {
  const { especialidadId } = useParams<{ especialidadId: string }>();
  const id = Number(especialidadId);
  const [especialidad, setEspecialidad] = useState<Especialidad | null | undefined>(undefined);
  const [medicos, setMedicos] = useState<Medico[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setEspecialidad(undefined);
    setMedicos(null);
    setError(null);
    listarEspecialidades()
      .then((todas) => setEspecialidad(todas.find((e) => e.id === id) ?? null))
      .catch(() => setError("No se pudo cargar la especialidad."));
    listarMedicos(id)
      .then(setMedicos)
      .catch(() => setError("No se pudo cargar la lista de médicos."));
  }, [id]);

  const Icono = iconoDeEspecialidad(especialidad?.nombre ?? "");

  if (especialidad === null) {
    return (
      <main className="page">
        <Link to="/medicos" className="volver">← Todas las especialidades</Link>
        <p className="error-banner">No existe esa especialidad.</p>
      </main>
    );
  }

  return (
    <main className="page">
      <Link to="/medicos" className="volver">← Todas las especialidades</Link>
      <div className="especialidad-cabecera">
        <span className="especialidades__icono"><Icono size={26} weight="duotone" aria-hidden="true" /></span>
        <div>
          <h1>{especialidad?.nombre ?? "Especialidad"}</h1>
          {especialidad?.descripcion && <p className="especialidades__intro">{especialidad.descripcion}</p>}
        </div>
      </div>

      {error && <p className="error-banner">{error}</p>}
      {!error && medicos === null && <p className="empty-state">Cargando…</p>}
      {medicos !== null && medicos.length === 0 && (
        <p className="empty-state">Todavía no hay médicos en esta especialidad.</p>
      )}
      <ul className="medicos-lista">
        {medicos?.map((medico) => (
          <li key={medico.id}>
            <Link
              to={`/medicos/${medico.id}`}
              state={{ desdeEspecialidad: { id, nombre: especialidad?.nombre } }}
              className="card medicos-lista__card"
            >
              <span className="medicos-lista__avatar"><Stethoscope size={22} weight="duotone" aria-hidden="true" /></span>
              <span className="medicos-lista__datos">
                <strong>{medico.nombre} {medico.apellido}</strong>
                <span className="medicos-lista__matricula">Matrícula {medico.matricula}</span>
                {medico.especialidades.length > 1 && (
                  <span className="medicos-lista__especialidades">
                    {medico.especialidades.map((e) => e.nombre).join(", ")}
                  </span>
                )}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  );
}
