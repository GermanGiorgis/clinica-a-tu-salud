package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarActivoRequest(@NotNull Boolean activo) {
}
