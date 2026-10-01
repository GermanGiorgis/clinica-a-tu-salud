package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Encuesta;

import java.time.LocalDateTime;

public record EncuestaResponse(
        Long id,
        Integer puntaje,
        String comentario,
        LocalDateTime creadaEn
) {
    public static EncuestaResponse from(Encuesta encuesta) {
        return new EncuestaResponse(encuesta.getId(), encuesta.getPuntaje(), encuesta.getComentario(), encuesta.getCreadaEn());
    }
}
