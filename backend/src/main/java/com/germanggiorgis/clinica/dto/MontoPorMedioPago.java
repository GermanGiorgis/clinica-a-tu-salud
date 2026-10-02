package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.MedioPago;

import java.math.BigDecimal;

/** Facturas emitidas con un medio de pago y su total; proyección de la consulta y dato de la respuesta. */
public record MontoPorMedioPago(MedioPago medioPago, long cantidad, BigDecimal total) {
}
