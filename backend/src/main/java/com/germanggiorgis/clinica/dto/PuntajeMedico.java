package com.germanggiorgis.clinica.dto;

/** Proyección interna: promedio y cantidad de encuestas que recibió un médico. */
public record PuntajeMedico(Long medicoId, Double promedio, long cantidad) {
}
