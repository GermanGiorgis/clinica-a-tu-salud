package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.ObraSocial;

public record ObraSocialResponse(
        Long id,
        String nombre,
        String telefono
) {
    public static ObraSocialResponse from(ObraSocial obraSocial) {
        return new ObraSocialResponse(obraSocial.getId(), obraSocial.getNombre(), obraSocial.getTelefono());
    }
}
