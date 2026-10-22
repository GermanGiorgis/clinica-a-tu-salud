package com.germanggiorgis.clinica.config;

import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import com.germanggiorgis.clinica.security.AdminSeeder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El reinicio de la demo en línea de verdad: borra TODA la base de pruebas y la vuelve a cargar con los datos de
 * demostración. Por eso, al terminar cada prueba deja la base como recién migrada (sin datos de demostración), igual que
 * la encontraron los demás tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef",
        "clinica.demo.reinicio=true",
        "clinica.demo.reinicio-token=token-de-prueba-del-reinicio",
        "spring.flyway.clean-disabled=false"
})
class ReinicioDeDemostracionIntegrationTest {

    private static final String CABECERA = "Bearer token-de-prueba-del-reinicio";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AdminSeeder adminSeeder;
    @Autowired private CuentasDeDemostracion cuentas;
    @Autowired private MedicosDeDemostracion medicos;

    @AfterEach
    void dejarLaBaseComoRecienMigrada() {
        flyway.clean();
        flyway.migrate();
    }

    private void encenderLosDatosDeDemostracion() {
        ReflectionTestUtils.setField(adminSeeder, "demoAdmin", true);
        ReflectionTestUtils.setField(cuentas, "activado", true);
        ReflectionTestUtils.setField(medicos, "activado", true);
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class);
    }

    private void crearVisitante() {
        usuarioRepository.saveAndFlush(Usuario.builder().email("visitante@qa.test").passwordHash("x").rol(Rol.PACIENTE)
                .nombre("Visitante").apellido("Demo").build());
    }

    @Test
    void sin_el_token_no_borra_nada() throws Exception {
        crearVisitante();

        mockMvc.perform(get("/api/demo/reiniciar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/demo/reiniciar").header("Authorization", "Bearer incorrecto")).andExpect(status().isUnauthorized());

        assertThat(usuarioRepository.existsByEmailIgnoreCase("visitante@qa.test")).isTrue();
    }

    @Test
    void con_el_token_borra_lo_que_cargaron_los_visitantes_y_deja_los_datos_de_demostracion() throws Exception {
        encenderLosDatosDeDemostracion();
        crearVisitante();
        jdbc.update("INSERT INTO especialidad (nombre) VALUES ('Especialidad inventada por un visitante')");

        mockMvc.perform(get("/api/demo/reiniciar").header("Authorization", CABECERA)).andExpect(status().isOk());

        assertThat(usuarioRepository.existsByEmailIgnoreCase("visitante@qa.test")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM especialidad WHERE nombre LIKE 'Especialidad inventada%'", Long.class)).isZero();
        assertThat(contar("especialidad")).isGreaterThanOrEqualTo(36);
        assertThat(contar("patologia")).isGreaterThanOrEqualTo(30);
        assertThat(contar("medico")).isGreaterThanOrEqualTo(60);
        for (String email : new String[] {"admin@clinica.test", CuentasDeDemostracion.EMAIL_PACIENTE,
                CuentasDeDemostracion.EMAIL_MEDICO, CuentasDeDemostracion.EMAIL_RECEPCION}) {
            assertThat(usuarioRepository.existsByEmailIgnoreCase(email)).as(email).isTrue();
        }
        assertThat(contar("turno")).as("el turno de ejemplo").isEqualTo(1);
    }

    @Test
    void reiniciar_dos_veces_seguidas_da_el_mismo_resultado() throws Exception {
        encenderLosDatosDeDemostracion();

        mockMvc.perform(get("/api/demo/reiniciar").header("Authorization", CABECERA)).andExpect(status().isOk());
        long medicosPrimera = contar("medico");
        long usuariosPrimera = contar("usuario");
        mockMvc.perform(get("/api/demo/reiniciar").header("Authorization", CABECERA)).andExpect(status().isOk());

        assertThat(contar("medico")).isEqualTo(medicosPrimera);
        assertThat(contar("usuario")).isEqualTo(usuariosPrimera);
    }
}
