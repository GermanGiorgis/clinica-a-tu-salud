package com.germanggiorgis.clinica.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de contraseñas: al menos 10 caracteres y como máximo 72 bytes (BCrypt ignora, o
 * directamente rechaza, lo que pasa de 72 bytes; se mide en bytes porque una "ñ" o una tilde
 * ocupan 2).
 */
@Documented
@Constraint(validatedBy = ClaveValidaValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClaveValida {

    String message() default "La contraseña debe tener al menos 10 caracteres y no más de 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
