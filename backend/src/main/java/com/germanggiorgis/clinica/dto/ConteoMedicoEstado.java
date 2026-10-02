package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.EstadoTurno;

/** Proyección interna: turnos de un médico en un estado (MetricasService la pivotea por médico). */
public record ConteoMedicoEstado(Long medicoId, EstadoTurno estado, long cantidad) {
}
