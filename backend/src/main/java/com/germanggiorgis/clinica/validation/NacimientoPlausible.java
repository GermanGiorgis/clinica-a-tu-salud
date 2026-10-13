package com.germanggiorgis.clinica.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Una fecha de nacimiento no anterior a 1900: descarta los años mal tipeados ("0198", "1098"). Que sea pasada lo exige @Past. */
@Documented
@Constraint(validatedBy = NacimientoPlausibleValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NacimientoPlausible {

    String message() default "El año de nacimiento no puede ser anterior a 1900";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
