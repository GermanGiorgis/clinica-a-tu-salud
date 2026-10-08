package com.germanggiorgis.clinica.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El catálogo de especialidades que carga la migración V10: es lo que ve cualquier persona, sin
 * iniciar sesión, al elegir con quién atenderse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class CatalogoEspecialidadesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void cualquiera_puede_ver_las_especialidades_de_una_clinica_sin_iniciar_sesion() throws Exception {
        mockMvc.perform(get("/api/especialidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItems(
                        "Traumatología y Ortopedia", "Dermatología", "Ginecología", "Cardiología", "Pediatría",
                        "Oftalmología", "Neurología", "Clínica Médica")));
    }

    @Test
    void el_catalogo_trae_una_descripcion_para_mostrar_en_cada_tarjeta() {
        Long sinDescripcion = jdbc.queryForObject(
                "SELECT count(*) FROM especialidad WHERE (descripcion IS NULL OR descripcion = '')"
                        + " AND nombre NOT LIKE 'QA %'", Long.class);

        assertThat(sinDescripcion).isZero();
    }

    @Test
    void ninguna_especialidad_quedo_duplicada_con_y_sin_tildes() {
        List<String> repetidas = jdbc.queryForList(
                "SELECT lower(translate(nombre, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) AS n FROM especialidad"
                        + " GROUP BY 1 HAVING count(*) > 1", String.class);

        assertThat(repetidas).isEmpty();
    }

    @Test
    void hay_al_menos_treinta_especialidades_cargadas() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM especialidad", Long.class)).isGreaterThanOrEqualTo(30);
    }
}
