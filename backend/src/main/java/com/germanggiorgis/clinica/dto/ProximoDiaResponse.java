package com.germanggiorgis.clinica.dto;

import java.time.LocalDate;

/** El primer día, desde una fecha, con algún horario libre; {@code fecha} es null si no queda ninguno en la ventana de reserva. */
public record ProximoDiaResponse(LocalDate fecha) {
}
