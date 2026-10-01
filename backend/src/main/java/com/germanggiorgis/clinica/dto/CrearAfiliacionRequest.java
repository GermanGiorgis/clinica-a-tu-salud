package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CrearAfiliacionRequest(
        @NotNull Long obraSocialId,
        @NotBlank @Size(max = 50) String numeroAfiliado,
        @Size(max = 100) String plan,
        @NotNull LocalDate vigenteDesde,
        LocalDate vigenteHasta
) {
}
