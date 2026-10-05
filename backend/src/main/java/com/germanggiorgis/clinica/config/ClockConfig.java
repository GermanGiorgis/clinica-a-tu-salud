package com.germanggiorgis.clinica.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * El reloj de la aplicación, con la zona horaria de la clínica. Lo que dependa de "ahora" tiene
 * que pedir este Clock en vez de llamar a {@code LocalDateTime.now()}, que usa la zona del servidor:
 * en un contenedor en UTC correría 3 horas contra Argentina. También permite fijar la hora en los tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${clinica.zona-horaria:America/Argentina/Buenos_Aires}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
