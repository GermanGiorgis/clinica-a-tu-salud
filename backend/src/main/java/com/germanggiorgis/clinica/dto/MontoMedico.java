package com.germanggiorgis.clinica.dto;

import java.math.BigDecimal;

/** Proyección interna: total facturado por los turnos de un médico. */
public record MontoMedico(Long medicoId, BigDecimal total) {
}
