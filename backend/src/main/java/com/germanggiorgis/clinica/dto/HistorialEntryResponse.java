package com.germanggiorgis.clinica.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Una entrada del historial de un paciente: un turno ya atendido, con su consulta completa. */
public record HistorialEntryResponse(
        Long turnoId,
        LocalDateTime inicio,
        String medicoNombre,
        String consultorioNombre,
        ConsultaResponse consulta
) {
}
