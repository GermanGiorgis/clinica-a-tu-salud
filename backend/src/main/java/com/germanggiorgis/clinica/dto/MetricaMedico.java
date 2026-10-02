package com.germanggiorgis.clinica.dto;

import java.math.BigDecimal;

public record MetricaMedico(
        Long medicoId,
        String nombre,
        long turnosAtendidos,
        long turnosAusentes,
        /** null si todavía no recibió ninguna encuesta. */
        Double puntajePromedio,
        long encuestas,
        BigDecimal facturado
) {
}
