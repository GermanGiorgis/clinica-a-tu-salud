package com.germanggiorgis.clinica.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    /** Reloj que se mueve a mano, para probar las ventanas de tiempo sin esperar. */
    private static class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-10-05T12:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();
    // 5 fallos por email, 8 por IP, 3 altas por IP por hora, ventana de 15 minutos, hasta 50 claves por mapa
    private final LoginAttemptService limite = new LoginAttemptService(reloj, 5, 8, 3, 15, 50);

    private void fallar(String email, String ip, int veces) {
        for (int i = 0; i < veces; i++) {
            limite.registrarFallo(email, ip);
        }
    }

    @Test
    void por_debajo_del_limite_no_bloquea() {
        fallar("ana@clinica.test", "1.1.1.1", 4);

        assertThatCode(() -> limite.verificarLoginPermitido("ana@clinica.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void al_llegar_al_limite_de_fallos_del_email_bloquea_con_429_y_dice_cuanto_esperar() {
        fallar("ana@clinica.test", "1.1.1.1", 5);

        assertThatThrownBy(() -> limite.verificarLoginPermitido("ana@clinica.test", "1.1.1.1"))
                .isInstanceOfSatisfying(DemasiadosIntentosException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(429);
                    assertThat(e.getSegundosDeEspera()).isBetween(1L, 900L);
                    assertThat(e.getHeaders().getFirst("Retry-After")).isEqualTo(String.valueOf(e.getSegundosDeEspera()));
                });
    }

    @Test
    void el_bloqueo_de_un_email_no_afecta_a_otro_email() {
        fallar("ana@clinica.test", "1.1.1.1", 5);

        assertThatCode(() -> limite.verificarLoginPermitido("beto@clinica.test", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void el_email_se_compara_sin_distinguir_mayusculas_ni_espacios() {
        fallar("Ana@Clinica.test", "1.1.1.1", 3);
        fallar("  ana@clinica.TEST ", "3.3.3.3", 2);

        assertThatThrownBy(() -> limite.verificarLoginPermitido("ANA@clinica.test", "4.4.4.4"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void un_login_exitoso_borra_los_fallos_de_ese_email() {
        fallar("ana@clinica.test", "1.1.1.1", 4);

        limite.registrarExito("ana@clinica.test");
        // Desde otra IP: el éxito borra los fallos del EMAIL, no los de la IP (que tiene su propio límite).
        fallar("ana@clinica.test", "2.2.2.2", 4);

        assertThatCode(() -> limite.verificarLoginPermitido("ana@clinica.test", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void pasada_la_ventana_se_desbloquea() {
        fallar("ana@clinica.test", "1.1.1.1", 5);

        reloj.avanzar(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> limite.verificarLoginPermitido("ana@clinica.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void la_espera_informada_se_acorta_a_medida_que_pasa_el_tiempo() {
        fallar("ana@clinica.test", "1.1.1.1", 5);
        long alPrincipio = esperaPara("ana@clinica.test", "1.1.1.1");

        reloj.avanzar(Duration.ofMinutes(5));
        long cincoMinutosDespues = esperaPara("ana@clinica.test", "1.1.1.1");

        assertThat(alPrincipio - cincoMinutosDespues).isBetween(299L, 301L);
    }

    @Test
    void los_fallos_viejos_salen_de_la_ventana_de_a_uno() {
        fallar("ana@clinica.test", "1.1.1.1", 3);
        reloj.avanzar(Duration.ofMinutes(10));
        fallar("ana@clinica.test", "1.1.1.1", 2);      // 5 dentro de la ventana: bloqueada
        assertThatThrownBy(() -> limite.verificarLoginPermitido("ana@clinica.test", "1.1.1.1"))
                .isInstanceOf(DemasiadosIntentosException.class);

        reloj.avanzar(Duration.ofMinutes(6));          // los 3 primeros ya pasaron los 15 min; quedan 2
        assertThatCode(() -> limite.verificarLoginPermitido("ana@clinica.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void una_misma_ip_que_prueba_muchos_emails_distintos_tambien_se_bloquea() {
        for (int i = 0; i < 8; i++) {
            limite.registrarFallo("usuario" + i + "@clinica.test", "9.9.9.9");
        }

        assertThatThrownBy(() -> limite.verificarLoginPermitido("otro@clinica.test", "9.9.9.9"))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThatCode(() -> limite.verificarLoginPermitido("otro@clinica.test", "8.8.8.8")).doesNotThrowAnyException();
    }

    @Test
    void el_registro_limita_las_altas_por_ip_por_hora() {
        for (int i = 0; i < 3; i++) {
            limite.verificarRegistroPermitido("5.5.5.5");
        }

        assertThatThrownBy(() -> limite.verificarRegistroPermitido("5.5.5.5")).isInstanceOf(DemasiadosIntentosException.class);
        assertThatCode(() -> limite.verificarRegistroPermitido("6.6.6.6")).doesNotThrowAnyException();

        reloj.avanzar(Duration.ofMinutes(61));
        assertThatCode(() -> limite.verificarRegistroPermitido("5.5.5.5")).doesNotThrowAnyException();
    }

    @Test
    void la_memoria_esta_acotada_aunque_lleguen_fallos_con_miles_de_emails_distintos() {
        for (int i = 0; i < 5_000; i++) {
            limite.registrarFallo("atacante" + i + "@clinica.test", "7.7.7." + (i % 250));
        }

        // 50 claves como máximo por mapa (tres mapas)
        assertThat(limite.clavesEnMemoria()).isLessThanOrEqualTo(150);
    }

    private long esperaPara(String email, String ip) {
        try {
            limite.verificarLoginPermitido(email, ip);
            return 0;
        } catch (DemasiadosIntentosException e) {
            return e.getSegundosDeEspera();
        }
    }
}
