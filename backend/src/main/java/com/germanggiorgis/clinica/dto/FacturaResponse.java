package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Factura;
import com.germanggiorgis.clinica.model.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FacturaResponse(
        Long id,
        Long turnoId,
        BigDecimal total,
        MedioPago medioPago,
        LocalDateTime emitidaEn,
        List<ItemFacturaResponse> items
) {
    public static FacturaResponse from(Factura factura, List<ItemFacturaResponse> items) {
        return new FacturaResponse(
                factura.getId(),
                factura.getTurno().getId(),
                factura.getTotal(),
                factura.getMedioPago(),
                factura.getEmitidaEn(),
                items
        );
    }
}
