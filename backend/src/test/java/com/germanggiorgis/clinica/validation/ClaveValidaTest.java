package com.germanggiorgis.clinica.validation;

import com.germanggiorgis.clinica.dto.RegistroPacienteRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ClaveValidaTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean esValida(String clave) {
        return validator.validate(new RegistroPacienteRequest("Ana", "Paz", "ana@clinica.test", clave, null, "30111222",
                LocalDate.of(1990, 1, 1))).stream().noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corta", "123456789", "         "})
    void rechaza_las_de_menos_de_10_caracteres(String clave) {
        assertThat(esValida(clave)).isFalse();
    }

    @Test
    void acepta_exactamente_10_caracteres() {
        assertThat(esValida("1234567890")).isTrue();
    }

    @Test
    void acepta_hasta_72_bytes() {
        assertThat(esValida("a".repeat(72))).isTrue();
    }

    @Test
    void rechaza_mas_de_72_bytes() {
        assertThat(esValida("a".repeat(73))).isFalse();
        assertThat(esValida("a".repeat(100))).isFalse();
    }

    @Test
    void mide_en_bytes_y_no_en_caracteres_porque_una_n_con_tilde_ocupa_dos() {
        // 37 caracteres de 2 bytes = 74 bytes: pasa de 72 aunque "solo" tenga 37 caracteres
        assertThat(esValida("ñ".repeat(37))).isFalse();
        assertThat(esValida("ñ".repeat(36))).isTrue();
    }

    @Test
    void una_clave_nula_la_rechaza_NotBlank_y_no_esta_validacion() {
        assertThat(ClaveValidaValidator.esValida(null)).isFalse();
        assertThat(new ClaveValidaValidator().isValid(null, null)).isTrue();
    }
}
