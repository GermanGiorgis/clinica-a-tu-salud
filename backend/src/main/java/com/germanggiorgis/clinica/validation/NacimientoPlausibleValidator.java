package com.germanggiorgis.clinica.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class NacimientoPlausibleValidator implements ConstraintValidator<NacimientoPlausible, LocalDate> {

    public static final LocalDate PRIMER_ANIO = LocalDate.of(1900, 1, 1);

    /** Un valor nulo lo rechaza @NotNull cuando el campo es obligatorio; acá solo se juzga el rango. */
    @Override
    public boolean isValid(LocalDate fecha, ConstraintValidatorContext context) {
        return fecha == null || !fecha.isBefore(PRIMER_ANIO);
    }
}
