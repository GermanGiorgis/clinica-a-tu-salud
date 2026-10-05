package com.germanggiorgis.clinica.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClockConfigTest {

    private TimeZone zonaOriginal;

    @BeforeEach
    void recordarLaZonaDeLaJvm() {
        zonaOriginal = TimeZone.getDefault();
    }

    @AfterEach
    void restaurarLaZonaDeLaJvm() {
        TimeZone.setDefault(zonaOriginal);
    }

    @Test
    void el_reloj_usa_la_zona_horaria_configurada() {
        Clock clock = new ClockConfig("America/Argentina/Buenos_Aires").clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    void al_arrancar_alinea_la_zona_de_la_jvm_con_la_de_la_clinica() {
        // Sin esto, los creadoEn = LocalDateTime.now() de las entidades saldrían en la hora del servidor.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        new ClockConfig("America/Argentina/Buenos_Aires").alinearLaZonaDeLaJvmConLaDeLaClinica();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Argentina/Buenos_Aires");
        // LocalDateTime.now() ya no sale en UTC: 3 horas de diferencia con el instante actual en UTC.
        long horasDeDiferencia = java.time.Duration.between(LocalDateTime.now(ZoneId.of("UTC")), LocalDateTime.now()).toHours();
        assertThat(horasDeDiferencia).isIn(-3L, -2L);
    }

    @Test
    void una_zona_inexistente_hace_fallar_el_arranque_en_vez_de_usar_otra_en_silencio() {
        assertThatThrownBy(() -> new ClockConfig("Marte/Olimpo")).isInstanceOf(java.time.DateTimeException.class);
    }
}
