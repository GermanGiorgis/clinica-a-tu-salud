package com.germanggiorgis.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

public record MetricasResponse(
        long totalTurnos,
        long pacientesAtendidos,
        /** % de ausentes sobre los turnos ya resueltos (atendidos + ausentes); null si todavía no hay ninguno. */
        Double tasaAusentismo,
        /** Promedio de 1 a 5 de todas las encuestas; null si no hay ninguna. */
        Double puntajePromedio,
        long totalEncuestas,
        BigDecimal facturacionTotal,
        List<ConteoPorEstado> turnosPorEstado,
        List<MontoPorMedioPago> facturacionPorMedioPago,
        List<MetricaMedico> medicos
) {
}
