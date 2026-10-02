package com.germanggiorgis.clinica.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chiquito helper compartido por los tests de servicios de este paquete. */
final class HttpStatusAssert {

    private HttpStatusAssert() {
    }

    static void assertStatus(ThrowingCallable callable, HttpStatus esperado) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(esperado);
    }
}
