package com.germanggiorgis.clinica.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class ClaveValidaValidator implements ConstraintValidator<ClaveValida, String> {

    public static final int MINIMO_CARACTERES = 10;
    public static final int MAXIMO_BYTES = 72;

    /** Un valor nulo lo rechaza @NotBlank; acá solo se juzga la longitud. */
    @Override
    public boolean isValid(String clave, ConstraintValidatorContext context) {
        return clave == null || esValida(clave);
    }

    public static boolean esValida(String clave) {
        return clave != null
                && clave.length() >= MINIMO_CARACTERES
                && clave.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES;
    }
}
