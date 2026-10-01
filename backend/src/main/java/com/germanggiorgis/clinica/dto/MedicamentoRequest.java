package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicamentoRequest(
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 100) String presentacion
) {
}
