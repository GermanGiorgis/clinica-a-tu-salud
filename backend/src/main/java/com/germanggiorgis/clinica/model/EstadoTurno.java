package com.germanggiorgis.clinica.model;

/**
 * Máquina de estados del turno (ver docs/modelo-datos.md).
 *
 * RESERVADO -> CONFIRMADO -> ATENDIDO
 * RESERVADO -> CANCELADO
 * CONFIRMADO -> CANCELADO
 * CONFIRMADO -> AUSENTE
 *
 * Las transiciones las valida el servicio, no esta clase.
 */
public enum EstadoTurno {
    RESERVADO,
    CONFIRMADO,
    ATENDIDO,
    AUSENTE,
    CANCELADO
}
