import { useEffect, useState } from "react";
import { accesosHistorial } from "../api/auditoria";
import { extraerError } from "../utils/errores";
import type { AccesoHistorial } from "../types";
import "./AuditoriaPage.css";

/** Quién abrió la historia clínica de quién y cuándo (médicos y admins; el paciente que mira la suya no figura). */
export function AuditoriaPage() {
  const [accesos, setAccesos] = useState<AccesoHistorial[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    accesosHistorial()
      .then(setAccesos)
      .catch((err) => setError(extraerError(err, "No se pudo cargar el registro de accesos.")));
  }, []);

  return (
    <main className="page">
      <h1>Accesos a historias clínicas</h1>
      <p className="auditoria__intro">Los 200 accesos más recientes. Este registro solo se agrega: nadie lo puede modificar desde la aplicación.</p>

      {error && <p className="error-banner">{error}</p>}
      {accesos === null && !error && <p className="empty-state">Cargando…</p>}
      {accesos !== null && accesos.length === 0 && <p className="empty-state">Todavía no se registró ningún acceso.</p>}

      {accesos !== null && accesos.length > 0 && (
        <div className="auditoria__tabla-scroll">
          <table className="auditoria__tabla">
            <thead>
              <tr>
                <th scope="col">Fecha y hora</th>
                <th scope="col">Quién accedió</th>
                <th scope="col">Rol</th>
                <th scope="col">Historial de</th>
              </tr>
            </thead>
            <tbody>
              {accesos.map((a) => (
                <tr key={a.id}>
                  <td>{new Date(a.accedidoEn).toLocaleString("es-AR", { dateStyle: "short", timeStyle: "medium" })}</td>
                  <td>
                    {a.usuarioNombre}
                    <span className="auditoria__email">{a.usuarioEmail}</span>
                  </td>
                  <td>{a.usuarioRol}</td>
                  <td>{a.pacienteNombre}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
