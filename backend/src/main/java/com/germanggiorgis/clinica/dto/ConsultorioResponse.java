package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Consultorio;

public record ConsultorioResponse(
        Long id,
        String nombre,
        String ubicacion,
        Integer piso
) {
    public static ConsultorioResponse from(Consultorio consultorio) {
        return new ConsultorioResponse(consultorio.getId(), consultorio.getNombre(), consultorio.getUbicacion(), consultorio.getPiso());
    }
}
