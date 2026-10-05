import { useEffect, useState } from "react";
import { IdentificationCard, UserCheck, UserMinus } from "@phosphor-icons/react";
import { pacientesPendientes, verificarPaciente } from "../api/pacientes";
import { cambiarActivo } from "../api/usuarios";
import { extraerError } from "../utils/errores";
import type { PacienteAVerificar } from "../types";
import "./PacientesPendientesPage.css";

/**
 * Verificación de identidad: quien se registra solo queda "sin verificar" hasta que el personal
 * compara, en persona, estos datos con su documento. Recién entonces se le puede reservar por DNI
 * y un médico puede ver su historial.
 */
export function PacientesPendientesPage() {
  const [pacientes, setPacientes] = useState<PacienteAVerificar[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [procesando, setProcesando] = useState<number | null>(null);

  function cargar() {
    pacientesPendientes()
      .then(setPacientes)
      .catch((err) => setError(extraerError(err, "No se pudo cargar la lista de pacientes.")));
  }

  useEffect(() => {
    cargar();
  }, []);

  async function ejecutar(paciente: PacienteAVerificar, accion: () => Promise<unknown>) {
    setProcesando(paciente.id);
    setError(null);
    try {
      await accion();
      cargar();
    } catch (err) {
      setError(extraerError(err));
    } finally {
      setProcesando(null);
    }
  }

  function handleVerificar(paciente: PacienteAVerificar) {
    return ejecutar(paciente, () => verificarPaciente(paciente.id));
  }

  function handleDesactivar(paciente: PacienteAVerificar) {
    const ok = window.confirm(
      `¿Desactivar la cuenta de ${paciente.nombre} ${paciente.apellido}? No va a poder iniciar sesión hasta que la reactives.`,
    );
    if (!ok) return;
    return ejecutar(paciente, () => cambiarActivo(paciente.usuarioId, false));
  }

  return (
    <main className="page">
      <h1>Pacientes por verificar</h1>
      <p className="pendientes__intro">
        Comparé estos datos con el documento de la persona antes de verificarla. Si una cuenta usa el DNI de
        otra persona, desactivala.
      </p>

      {error && <p className="error-banner">{error}</p>}
      {pacientes === null && !error && <p className="empty-state">Cargando…</p>}
      {pacientes !== null && pacientes.length === 0 && <p className="empty-state">No hay pacientes pendientes de verificación.</p>}

      <ul className="pendientes__lista">
        {pacientes?.map((p) => (
          <li key={p.id} className="card pendientes__item">
            <div className="pendientes__datos">
              <strong>{p.nombre} {p.apellido}</strong>
              <span><IdentificationCard size={15} aria-hidden="true" /> DNI {p.dni}</span>
              <span>Nacimiento: {new Date(`${p.fechaNacimiento}T00:00:00`).toLocaleDateString("es-AR")}</span>
              <span>{p.email}</span>
              {p.telefono && <span>Tel. {p.telefono}</span>}
            </div>
            <div className="pendientes__acciones">
              <button type="button" className="btn btn-primary btn-sm" disabled={procesando === p.id} onClick={() => handleVerificar(p)}>
                <UserCheck size={16} aria-hidden="true" /> Verificar identidad
              </button>
              <button type="button" className="btn btn-danger btn-sm" disabled={procesando === p.id} onClick={() => handleDesactivar(p)}>
                <UserMinus size={16} aria-hidden="true" /> Desactivar cuenta
              </button>
            </div>
          </li>
        ))}
      </ul>
    </main>
  );
}
