import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listarMedicos } from "../api/medicos";
import type { Medico } from "../types";
import "./MedicosPage.css";

export function MedicosPage() {
  const [medicos, setMedicos] = useState<Medico[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listarMedicos()
      .then(setMedicos)
      .catch(() => setError("No se pudo cargar la lista de médicos."));
  }, []);

  return (
    <main className="page">
      <h1>Médicos</h1>
      {error && <p className="auth-form__error">{error}</p>}
      {!error && medicos === null && <p>Cargando…</p>}
      {medicos !== null && medicos.length === 0 && <p>Todavía no hay médicos cargados.</p>}
      <ul className="medicos-lista">
        {medicos?.map((medico) => (
          <li key={medico.id}>
            <Link to={`/medicos/${medico.id}`} className="medicos-lista__card">
              <strong>{medico.nombre} {medico.apellido}</strong>
              <span className="medicos-lista__matricula">Matrícula {medico.matricula}</span>
              <span className="medicos-lista__especialidades">
                {medico.especialidades.length > 0
                  ? medico.especialidades.map((e) => e.nombre).join(", ")
                  : "Sin especialidad cargada"}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  );
}
