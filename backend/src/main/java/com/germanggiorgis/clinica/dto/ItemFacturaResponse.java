package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.ItemFactura;

import java.math.BigDecimal;

public record ItemFacturaResponse(
        Long id,
        String descripcion,
        BigDecimal importe
) {
    public static ItemFacturaResponse from(ItemFactura item) {
        return new ItemFacturaResponse(item.getId(), item.getDescripcion(), item.getImporte());
    }
}
