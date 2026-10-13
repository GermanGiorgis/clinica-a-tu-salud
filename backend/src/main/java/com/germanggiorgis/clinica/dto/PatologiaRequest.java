package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PatologiaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotEmpty List<@NotNull Long> especialidadIds
) {
}
