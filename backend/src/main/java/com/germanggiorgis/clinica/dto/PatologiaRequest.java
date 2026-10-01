package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PatologiaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull Long especialidadId
) {
}
