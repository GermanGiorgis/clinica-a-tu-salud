package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ItemFacturaRequest(
        @NotBlank @Size(max = 200) String descripcion,
        @NotNull @PositiveOrZero BigDecimal importe
) {
}
