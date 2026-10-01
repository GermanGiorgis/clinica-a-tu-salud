import { Link } from "react-router-dom";
import { CheckCircle, Circle, FirstAidKit, Shield, Stethoscope, User } from "@phosphor-icons/react";

const ROLES = [
  {
    nombre: "Paciente",
    icono: User,
    detalle: "Busca médicos por especialidad o por patología, reserva y cancela sus turnos, consulta su historial y recetas.",
  },
  {
    nombre: "Médico",
    icono: Stethoscope,
    detalle: "Ve y gestiona su propia agenda (reserva, confirma, cancela, marca ausente), atiende turnos, carga consulta, diagnóstico y receta.",
  },
  {
    nombre: "Admin",
    icono: Shield,
    detalle: "Administra usuarios y catálogos, y puede gestionar la agenda de cualquier médico igual que el propio médico.",
  },
];

const FASES = [
  { nombre: "Turnos", detalle: "login, roles, agenda y reserva", hecha: true },
  { nombre: "Atención clínica", detalle: "consulta, diagnóstico, receta e historial", hecha: true },
  { nombre: "Complementos", detalle: "estudios, encuestas, cobros y métricas", hecha: false },
];

export function HomePage() {
  return (
    <main className="page">
      <section className="home-hero">
        <span className="home-hero__badge">
          <FirstAidKit size={16} weight="bold" aria-hidden="true" /> Fases 1 y 2 hechas
        </span>
        <h1>Gestión de turnos y atención médica</h1>
        <p className="home-hero__texto">
          Los pacientes reservan online, los médicos administran su agenda y
          cargan la historia clínica, y el administrador coordina el día a
          día de la clínica.
        </p>
        <div className="home-hero__acciones">
          <Link to="/medicos" className="btn btn-primary">Buscar un médico</Link>
          <Link to="/registro" className="btn btn-secondary">Crear cuenta de paciente</Link>
        </div>
      </section>

      <h2>Roles</h2>
      <ul className="roles">
        {ROLES.map((rol) => (
          <li key={rol.nombre} className="card roles__item">
            <rol.icono size={26} weight="duotone" aria-hidden="true" />
            <h3>{rol.nombre}</h3>
            <p>{rol.detalle}</p>
          </li>
        ))}
      </ul>

      <h2>Fases del proyecto</h2>
      <ol className="fases">
        {FASES.map((fase) => (
          <li key={fase.nombre} data-hecha={fase.hecha}>
            {fase.hecha
              ? <CheckCircle size={18} weight="fill" aria-hidden="true" />
              : <Circle size={18} aria-hidden="true" />}
            <span><strong>{fase.nombre}</strong> — {fase.detalle}</span>
          </li>
        ))}
      </ol>
    </main>
  );
}
