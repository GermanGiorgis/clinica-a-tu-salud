package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.EstadoTurno;

/** Cantidad de turnos en un estado; sirve tanto de proyección de la consulta como de dato de la respuesta. */
public record ConteoPorEstado(EstadoTurno estado, long cantidad) {
}
