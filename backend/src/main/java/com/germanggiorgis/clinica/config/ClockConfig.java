package com.germanggiorgis.clinica.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * La hora de la aplicación es la de la clínica, no la del servidor. En un contenedor en UTC,
 * "ahora" correría 3 horas contra Argentina: se ofrecerían como libres horarios que ya pasaron y la
 * regla de cancelar con 2 horas de anticipación quedaría corrida.
 *
 * <ul>
 *   <li>Las reglas de negocio piden este {@link Clock} (y los tests lo pueden fijar).</li>
 *   <li>Lo que no se puede inyectar (los {@code creadoEn = LocalDateTime.now()} de las entidades) usa
 *       la zona por defecto de la JVM, que acá se alinea con la de la clínica al arrancar.</li>
 * </ul>
 */
@Configuration
public class ClockConfig {

    private final ZoneId zona;

    public ClockConfig(@Value("${clinica.zona-horaria:America/Argentina/Buenos_Aires}") String zona) {
        this.zona = ZoneId.of(zona);
    }

    @PostConstruct
    void alinearLaZonaDeLaJvmConLaDeLaClinica() {
        TimeZone.setDefault(TimeZone.getTimeZone(zona));
    }

    @Bean
    public Clock clock() {
        return Clock.system(zona);
    }
}
