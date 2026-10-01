const ROLES = [
  {
    nombre: "Paciente",
    detalle: "Busca médicos por especialidad, reserva y cancela turnos, consulta su historial y recetas.",
  },
  {
    nombre: "Médico",
    detalle: "Ve y gestiona su propia agenda (reserva, confirma, cancela, marca ausente), atiende turnos, carga consulta, diagnóstico y receta.",
  },
  {
    nombre: "Admin",
    detalle: "Administra usuarios y catálogos, y puede gestionar la agenda de cualquier médico.",
  },
];

const FASES = [
  { nombre: "Turnos", detalle: "login, roles, agenda y reserva", activa: true },
  { nombre: "Atención clínica", detalle: "consulta, diagnóstico, receta e historial", activa: false },
  { nombre: "Complementos", detalle: "estudios, encuestas, cobros y métricas", activa: false },
];

export function HomePage() {
  return (
    <main className="page">
      <span className="page__estado">En construcción — Fase 1</span>
      <h1>Clínica</h1>
      <p className="page__origen">
        Sistema web de gestión de turnos y atención médica. Los pacientes
        reservan online, los médicos administran su agenda y cargan la
        historia clínica, y el personal administrativo coordina el día a día.
      </p>

      <h2>Roles</h2>
      <ul className="roles">
        {ROLES.map((rol) => (
          <li key={rol.nombre}>
            <h3>{rol.nombre}</h3>
            <p>{rol.detalle}</p>
          </li>
        ))}
      </ul>

      <h2>Fases</h2>
      <ol className="fases">
        {FASES.map((fase) => (
          <li key={fase.nombre} data-activa={fase.activa}>
            {fase.nombre} — {fase.detalle}
            {fase.activa && " (en curso)"}
          </li>
        ))}
      </ol>
    </main>
  );
}
