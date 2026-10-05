package com.germanggiorgis.clinica.controller;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void un_error_inesperado_es_500_y_no_filtra_el_detalle_interno() {
        ResponseEntity<Map<String, String>> r = handler.handleOtros(
                new RuntimeException("ERROR: insert into usuario ... password_hash=$2a$10$secretosecreto"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).containsKey("error");
        assertThat(r.getBody().get("error")).doesNotContain("insert", "password_hash", "secreto");
    }

    @Test
    void un_error_http_que_spring_ya_modela_conserva_su_codigo() {
        ResponseEntity<Map<String, String>> r = handler.handleOtros(new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(r.getBody().get("error")).isEqualTo("Ese método no está permitido en esta ruta");
    }

    @Test
    void una_violacion_de_restriccion_es_409_y_no_muestra_el_sql() {
        ResponseEntity<Map<String, String>> r = handler.handleDataIntegrity(
                new DataIntegrityViolationException("could not execute statement", new RuntimeException("violates foreign key constraint fk_turno_consultorio")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().get("error")).doesNotContain("fk_turno_consultorio", "foreign key");
    }

    @Test
    void un_deadlock_entre_dos_operaciones_simultaneas_es_409_y_no_500() {
        ResponseEntity<Map<String, String>> r = handler.handleConcurrencia(
                new CannotAcquireLockException("could not execute statement", new RuntimeException("ERROR: deadlock detected")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().get("error")).doesNotContain("deadlock", "SQL");
    }

    @Test
    void una_modificacion_simultanea_es_409_con_un_mensaje_que_dice_que_hay_que_recargar() {
        ResponseEntity<Map<String, String>> r = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Turno", 5L));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().get("error")).contains("Recargá");
    }
}
