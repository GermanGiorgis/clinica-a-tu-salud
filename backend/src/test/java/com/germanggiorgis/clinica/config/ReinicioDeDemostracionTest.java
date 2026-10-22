package com.germanggiorgis.clinica.config;

import com.germanggiorgis.clinica.security.AdminSeeder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReinicioDeDemostracionTest {

    private static final String TOKEN = "secreto-del-cron-de-prueba";

    @Mock private Flyway flyway;
    @Mock private AdminSeeder adminSeeder;
    @Mock private CuentasDeDemostracion cuentas;
    @Mock private MedicosDeDemostracion medicos;

    @InjectMocks private ReinicioDeDemostracion reinicio;

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(reinicio, "habilitado", true);
        ReflectionTestUtils.setField(reinicio, "token", TOKEN);
    }

    @Test
    void con_el_reinicio_encendido_y_un_token_esta_habilitado() {
        assertThat(reinicio.estaHabilitado()).isTrue();
    }

    @Test
    void apagado_no_esta_habilitado_aunque_haya_token() {
        ReflectionTestUtils.setField(reinicio, "habilitado", false);

        assertThat(reinicio.estaHabilitado()).isFalse();
        assertThat(reinicio.tokenValido("Bearer " + TOKEN)).isFalse();
    }

    @Test
    void sin_token_configurado_no_esta_habilitado_ni_acepta_un_encabezado_vacio() {
        ReflectionTestUtils.setField(reinicio, "token", "");

        assertThat(reinicio.estaHabilitado()).isFalse();
        assertThat(reinicio.tokenValido("Bearer ")).isFalse();
        assertThat(reinicio.tokenValido("Bearer")).isFalse();
    }

    @Test
    void acepta_solo_el_token_exacto_con_el_prefijo_bearer() {
        assertThat(reinicio.tokenValido("Bearer " + TOKEN)).isTrue();

        assertThat(reinicio.tokenValido(null)).isFalse();
        assertThat(reinicio.tokenValido("")).isFalse();
        assertThat(reinicio.tokenValido(TOKEN)).isFalse();
        assertThat(reinicio.tokenValido("Basic " + TOKEN)).isFalse();
        assertThat(reinicio.tokenValido("Bearer " + TOKEN + "x")).isFalse();
        assertThat(reinicio.tokenValido("Bearer " + TOKEN.substring(1))).isFalse();
        assertThat(reinicio.tokenValido("bearer " + TOKEN)).isFalse();
    }

    @Test
    void reiniciar_borra_recrea_y_vuelve_a_cargar_los_datos_en_ese_orden() {
        reinicio.reiniciar();

        InOrder orden = inOrder(flyway, adminSeeder, cuentas, medicos);
        orden.verify(flyway).clean();
        orden.verify(flyway).migrate();
        orden.verify(adminSeeder).run(any());
        orden.verify(cuentas).run(any());
        orden.verify(medicos).run(any());
    }

    @Test
    void apagado_se_niega_a_borrar_nada() {
        ReflectionTestUtils.setField(reinicio, "habilitado", false);

        assertThatThrownBy(() -> reinicio.reiniciar()).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(flyway, adminSeeder, cuentas, medicos);
    }
}
