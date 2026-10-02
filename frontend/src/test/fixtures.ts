import type { HistorialEntry, Metricas, Turno, Usuario } from "../types";

export function usuario(rol: Usuario["rol"], extra: Partial<Usuario> = {}): Usuario {
  return { email: `${rol.toLowerCase()}@clinica.test`, nombre: "Nora", apellido: "Prueba", rol, ...extra };
}

export function turno(extra: Partial<Turno> = {}): Turno {
  return {
    id: 1,
    medicoId: 7,
    medicoNombre: "Pedro Prueba",
    pacienteId: 3,
    pacienteNombre: "Pablo Paciente",
    consultorioId: 4,
    consultorioNombre: "Consultorio B",
    inicio: "2030-01-10T11:00:00",
    fin: "2030-01-10T11:30:00",
    estado: "RESERVADO",
    motivoConsulta: null,
    motivoCancelacionNombre: null,
    obraSocialNombre: null,
    ...extra,
  };
}

export function metricas(extra: Partial<Metricas> = {}): Metricas {
  return {
    totalTurnos: 6,
    pacientesAtendidos: 1,
    tasaAusentismo: 33.33,
    puntajePromedio: 4,
    totalEncuestas: 2,
    facturacionTotal: 35500.5,
    turnosPorEstado: [
      { estado: "RESERVADO", cantidad: 1 },
      { estado: "CONFIRMADO", cantidad: 0 },
      { estado: "ATENDIDO", cantidad: 2 },
      { estado: "AUSENTE", cantidad: 1 },
      { estado: "CANCELADO", cantidad: 2 },
    ],
    facturacionPorMedioPago: [
      { medioPago: "EFECTIVO", cantidad: 1, total: 12000 },
      { medioPago: "TARJETA", cantidad: 0, total: 0 },
      { medioPago: "TRANSFERENCIA", cantidad: 0, total: 0 },
      { medioPago: "OBRA_SOCIAL", cantidad: 1, total: 23500.5 },
    ],
    medicos: [
      { medicoId: 7, nombre: "Pedro Prueba", turnosAtendidos: 2, turnosAusentes: 1, puntajePromedio: 4, encuestas: 2, facturado: 35500.5 },
      { medicoId: 5, nombre: "Laura Lopez", turnosAtendidos: 0, turnosAusentes: 0, puntajePromedio: null, encuestas: 0, facturado: 0 },
    ],
    ...extra,
  };
}

export function entradaHistorial(extra: Partial<HistorialEntry> = {}): HistorialEntry {
  return {
    turnoId: 5,
    inicio: "2026-10-01T10:00:00",
    medicoNombre: "Pedro Prueba",
    consultorioNombre: "Consultorio B",
    consulta: {
      id: 1,
      turnoId: 5,
      notas: "Dolor de cabeza leve desde hace 2 días.",
      creadaEn: "2026-10-01T10:30:00",
      diagnosticos: [{ id: 1, descripcion: "Cefalea tensional", codigoCie10: "G44.2" }],
      recetas: [
        {
          id: 1,
          consultaId: 1,
          observaciones: "Tomar con las comidas",
          emitidaEn: "2026-10-01T10:30:00",
          items: [{ id: 1, medicamentoId: 1, medicamentoNombre: "Ibuprofeno", dosis: "1 comprimido", frecuencia: "cada 8 horas", duracionDias: 5 }],
        },
      ],
      ordenesEstudio: [
        {
          id: 1,
          estudioId: 1,
          estudioNombre: "Hemograma completo",
          estado: "COMPLETADA",
          indicaciones: null,
          emitidaEn: "2026-10-01T10:30:00",
          resultado: { id: 1, resultado: "Todo normal", cargadoEn: "2026-10-02T09:00:00" },
        },
        {
          id: 2,
          estudioId: 2,
          estudioNombre: "Radiografía de tórax",
          estado: "SOLICITADA",
          indicaciones: null,
          emitidaEn: "2026-10-01T10:30:00",
          resultado: null,
        },
      ],
    },
    ...extra,
  };
}
