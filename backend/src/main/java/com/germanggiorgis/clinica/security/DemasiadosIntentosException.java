package com.germanggiorgis.clinica.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 429: se superó el límite de intentos. Lleva el encabezado Retry-After con los segundos de espera. */
public class DemasiadosIntentosException extends ResponseStatusException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(long segundosDeEspera) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Probá de nuevo en " + descripcion(segundosDeEspera));
        this.segundosDeEspera = segundosDeEspera;
    }

    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(segundosDeEspera));
        return headers;
    }

    private static String descripcion(long segundos) {
        if (segundos < 60) {
            return segundos + " segundo(s)";
        }
        long minutos = (segundos + 59) / 60;
        return minutos + " minuto(s)";
    }
}
