package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los síntomas que carga la migración V12: quien no sabe a qué especialista ir dice "qué le pasa" y ve las
 * especialidades que corresponden (una o varias).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class CatalogoSintomasIntegrationTest extends BaseIntegracionConDatos {

    private List<Map<String, Object>> especialidadesDe(String sintoma) {
        return jdbc.queryForList(
                "SELECT e.nombre FROM patologia p JOIN patologia_especialidad pe ON pe.patologia_id = p.id"
                        + " JOIN especialidad e ON e.id = pe.especialidad_id WHERE p.nombre = ? ORDER BY e.nombre", sintoma);
    }

    private long idEspecialidad(String nombre) {
        return jdbc.queryForObject("SELECT id FROM especialidad WHERE nombre = ?", Long.class, nombre);
    }

    @Test
    void hay_al_menos_diez_sintomas_basicos_y_cualquiera_los_puede_ver_sin_iniciar_sesion() throws Exception {
        mockMvc.perform(get("/api/patologias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Dolor de cabeza')]").exists())
                .andExpect(jsonPath("$[?(@.nombre == 'Dolor de estómago o de panza')]").exists())
                .andExpect(jsonPath("$[?(@.nombre == 'Lunares o manchas en la piel')]").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patologia", Long.class)).isGreaterThanOrEqualTo(10);
    }

    @Test
    void un_sintoma_de_la_piel_lleva_a_dermatologia() throws Exception {
        mockMvc.perform(get("/api/patologias"))
                .andExpect(jsonPath("$[?(@.nombre == 'Lunares o manchas en la piel')].especialidades[*].nombre", containsInAnyOrder("Dermatología")));
    }

    @Test
    void el_dolor_de_cabeza_lleva_a_neurologia_y_a_clinica_medica() throws Exception {
        mockMvc.perform(get("/api/patologias"))
                .andExpect(jsonPath("$[?(@.nombre == 'Dolor de cabeza')].especialidades[*].nombre", containsInAnyOrder("Neurología", "Clínica Médica")));
    }

    @Test
    void el_dolor_de_estomago_lleva_a_gastroenterologia_y_a_clinica_medica() {
        assertThat(especialidadesDe("Dolor de estómago o de panza")).extracting(f -> f.get("nombre"))
                .containsExactly("Clínica Médica", "Gastroenterología");
    }

    @Test
    void ningun_sintoma_del_catalogo_queda_sin_especialidad() {
        List<String> huerfanos = jdbc.queryForList(
                "SELECT nombre FROM patologia WHERE id NOT IN (SELECT patologia_id FROM patologia_especialidad)"
                        + " AND nombre NOT LIKE 'QA %'", String.class);

        assertThat(huerfanos).isEmpty();
    }

    @Test
    void el_admin_crea_un_sintoma_con_varias_especialidades_y_las_puede_cambiar() throws Exception {
        String admin = token(usuario(Rol.ADMIN));
        long neurologia = idEspecialidad("Neurología");
        long clinica = idEspecialidad("Clínica Médica");
        long cardiologia = idEspecialidad("Cardiología");

        String respuesta = mockMvc.perform(post("/api/patologias").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + PREFIJO + "Hormigueo\",\"especialidadIds\":[" + neurologia + "," + clinica + "," + neurologia + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.especialidades", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(respuesta, "$.id");

        mockMvc.perform(put("/api/patologias/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + PREFIJO + "Hormigueo\",\"especialidadIds\":[" + cardiologia + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades[*].nombre", containsInAnyOrder("Cardiología")));
        assertThat(especialidadesDe(PREFIJO + "Hormigueo")).extracting(f -> f.get("nombre")).containsExactly("Cardiología");
    }

    @Test
    void un_sintoma_sin_especialidades_o_con_una_inexistente_se_rechaza() throws Exception {
        String admin = token(usuario(Rol.ADMIN));

        mockMvc.perform(post("/api/patologias").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + PREFIJO + "Nada\",\"especialidadIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.especialidadIds").value("es obligatorio"));
        mockMvc.perform(post("/api/patologias").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + PREFIJO + "Nada\",\"especialidadIds\":[999999999]}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patologia WHERE nombre = ?", Long.class, PREFIJO + "Nada")).isZero();
    }

    @Test
    void una_especialidad_que_figura_en_un_sintoma_no_se_puede_borrar() throws Exception {
        String admin = token(usuario(Rol.ADMIN));
        Long dermatologia = jdbc.queryForObject("SELECT id FROM especialidad WHERE nombre = 'Dermatología'", Long.class);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/especialidades/" + dermatologia)
                        .header("Authorization", admin))
                .andExpect(status().isConflict());
        assertThat(idEspecialidad("Dermatología")).isEqualTo(dermatologia);
    }

    @Test
    void borrar_un_sintoma_no_borra_las_especialidades() throws Exception {
        String admin = token(usuario(Rol.ADMIN));
        jdbc.update("INSERT INTO patologia (nombre) VALUES (?)", PREFIJO + "Temporal");
        Long id = jdbc.queryForObject("SELECT id FROM patologia WHERE nombre = ?", Long.class, PREFIJO + "Temporal");
        jdbc.update("INSERT INTO patologia_especialidad VALUES (?, ?)", id, idEspecialidad("Cardiología"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/patologias/" + id)
                        .header("Authorization", admin))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM especialidad WHERE nombre = 'Cardiología'", Long.class)).isEqualTo(1);
    }
}
