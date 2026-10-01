package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Rol;

public record AuthResponse(
        String token,
        String email,
        String nombre,
        String apellido,
        Rol rol
) {
}
