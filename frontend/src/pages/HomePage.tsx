import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, CalendarCheck, ClipboardText, LockKey, Stethoscope, SquaresFour } from "@phosphor-icons/react";
import { listarEspecialidades } from "../api/especialidades";
import { listarPatologias } from "../api/patologias";
import { useAuth } from "../context/AuthContext";
import { iconoDeEspecialidad, normalizar } from "../utils/especialidades";
import { esPersonalDeLaClinica } from "../utils/roles";
import type { Especialidad, Patologia } from "../types";
import "./HomePage.css";

export const NOMBRE_DE_LA_CLINICA = "Clínica a tu salud";

/** Las especialidades que más se buscan, en el orden en que se muestran (si el catálogo no las tiene, se omiten). */
const ESPECIALIDADES_DESTACADAS = [
  "Clínica Médica", "Pediatría", "Ginecología", "Dermatología",
  "Traumatología", "Cardiología", "Oftalmología", "Odontología",
];

/** Los motivos de consulta más habituales, para que quien no sabe a qué especialista ir empiece con un clic. */
const SINTOMAS_FRECUENTES = [
  "Dolor de cabeza", "Dolor de estómago", "Lunares o manchas", "Dolor de espalda", "Fiebre", "Tos, falta de aire",
];

const MOTIVOS = [
  {
    icono: CalendarCheck,
    titulo: "Reservá cuando quieras",
    detalle: "Mirá los horarios libres de cada médico y sacá tu turno online. Si cambia tu agenda, lo cancelás o lo movés desde Mis turnos.",
  },
  {
    icono: SquaresFour,
    titulo: "Todas las especialidades",
    detalle: "Desde clínica médica y pediatría hasta cardiología, dermatología o traumatología, en un solo lugar.",
  },
  {
    icono: ClipboardText,
    titulo: "Tu historial a mano",
    detalle: "Consultas, diagnósticos, recetas y tu cobertura médica, ordenados en tu cuenta.",
  },
  {
    icono: LockKey,
    titulo: "Tus datos, protegidos",
    detalle: "Tu historia clínica la ven solo vos y tu médico, y cada acceso queda registrado.",
  },
];

/** La especialidad del catálogo que corresponde a un nombre corto como "Traumatología" (sin importar tildes ni mayúsculas). */
function buscarEspecialidad(catalogo: Especialidad[], nombre: string): Especialidad | undefined {
  const buscado = normalizar(nombre);
  return catalogo.find((e) => normalizar(e.nombre).startsWith(buscado));
}

/** El síntoma del catálogo cuyo nombre empieza con el texto dado (el catálogo dice "Dolor de estómago o de panza"). */
function buscarSintoma(catalogo: Patologia[], texto: string): Patologia | undefined {
  const buscado = normalizar(texto);
  return catalogo.find((p) => normalizar(p.nombre).startsWith(buscado));
}

export function HomePage() {
  const { estaAutenticado, usuario } = useAuth();
  const [especialidades, setEspecialidades] = useState<Especialidad[]>([]);
  const [sintomas, setSintomas] = useState<Patologia[]>([]);

  // La portada tiene que abrir aunque el servidor no conteste: sin catálogos solo faltan esos bloques.
  useEffect(() => {
    listarEspecialidades().then(setEspecialidades).catch(() => {});
    listarPatologias().then(setSintomas).catch(() => {});
  }, []);

  const destacadas = useMemo(
    () => ESPECIALIDADES_DESTACADAS.map((n) => buscarEspecialidad(especialidades, n)).filter((e): e is Especialidad => !!e),
    [especialidades],
  );
  const frecuentes = useMemo(
    () => SINTOMAS_FRECUENTES.map((s) => buscarSintoma(sintomas, s)).filter((p): p is Patologia => !!p),
    [sintomas],
  );

  const esPersonal = esPersonalDeLaClinica(usuario?.rol);
  const esPaciente = usuario?.rol === "PACIENTE";

  return (
    <main className="page home">
      <section className="home__hero">
        <div className="home__hero-texto">
          <p className="home__marca"><Stethoscope size={18} weight="bold" aria-hidden="true" /> {NOMBRE_DE_LA_CLINICA}</p>
          <h1>Pedí tu turno en minutos</h1>
          <p className="home__bajada">
            Elegí la especialidad, mirá los horarios disponibles y reservá online. Si no sabés a cuál ir, contanos qué te pasa.
          </p>
          <div className="home__acciones">
            {esPersonal ? (
              <Link to="/agenda" className="btn btn-primary">Ir a la agenda</Link>
            ) : (
              <Link to="/medicos" className="btn btn-primary">Sacar un turno</Link>
            )}
            {!estaAutenticado && <Link to="/registro" className="btn btn-secondary">Crear cuenta de paciente</Link>}
            {esPaciente && <Link to="/mis-turnos" className="btn btn-secondary">Mis turnos</Link>}
          </div>
        </div>

        {frecuentes.length > 0 && (
          <div className="home__sintomas card" aria-labelledby="home-que-te-pasa">
            <h2 id="home-que-te-pasa">¿Qué te pasa?</h2>
            <p>Elegí lo que sentís y te mostramos las especialidades que lo atienden.</p>
            <ul>
              {frecuentes.map((s) => (
                <li key={s.id}>
                  <Link to={`/medicos?sintoma=${s.id}`}>{s.nombre}</Link>
                </li>
              ))}
            </ul>
            <Link to="/medicos" className="home__ver-mas">Ver todos los síntomas <ArrowRight size={14} aria-hidden="true" /></Link>
          </div>
        )}
      </section>

      {destacadas.length > 0 && (
        <section className="home__seccion" aria-labelledby="home-especialidades">
          <div className="home__seccion-cabecera">
            <h2 id="home-especialidades">Especialidades</h2>
            <Link to="/medicos" className="home__ver-mas">
              Ver las {especialidades.length} especialidades <ArrowRight size={14} aria-hidden="true" />
            </Link>
          </div>
          <ul className="home__especialidades">
            {destacadas.map((e) => {
              const Icono = iconoDeEspecialidad(e.nombre);
              return (
                <li key={e.id}>
                  <Link to={`/medicos/especialidad/${e.id}`} className="card home__especialidad">
                    <span className="home__icono"><Icono size={24} weight="duotone" aria-hidden="true" /></span>
                    {e.nombre}
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      <section className="home__seccion" aria-labelledby="home-motivos">
        <h2 id="home-motivos">Una clínica pensada para vos</h2>
        <ul className="home__motivos">
          {MOTIVOS.map(({ icono: Icono, titulo, detalle }) => (
            <li key={titulo}>
              <span className="home__icono"><Icono size={24} weight="duotone" aria-hidden="true" /></span>
              <h3>{titulo}</h3>
              <p>{detalle}</p>
            </li>
          ))}
        </ul>
      </section>

      {!estaAutenticado && (
        <section className="home__invitacion">
          <div>
            <h2>¿Primera vez?</h2>
            <p>Creá tu cuenta, reservá tu primer turno y encontrá todo en un mismo lugar. Para que podamos darte turnos y ver tu historial, llevá tu DNI la primera vez que vengas.</p>
          </div>
          <div className="home__acciones">
            <Link to="/registro" className="btn btn-primary">Crear cuenta</Link>
            <Link to="/login" className="btn btn-secondary">Ya tengo cuenta</Link>
          </div>
        </section>
      )}

      <p className="home__urgencias">
        Si es una urgencia (dolor fuerte en el pecho, falta de aire, desmayo), no esperes un turno: llamá al 107 o andá a una guardia.
      </p>
    </main>
  );
}
