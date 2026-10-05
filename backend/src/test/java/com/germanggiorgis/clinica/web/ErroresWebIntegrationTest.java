package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que ningún error salga con la forma de Spring Boot ni como 500 cuando la causa es del
 * cliente. Sin @Transactional: una restricción de la base solo salta al hacer commit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class ErroresWebIntegrationTest extends BaseIntegracionConDatos {

    @Test
    void borrar_un_consultorio_que_esta_en_uso_da_409_y_no_500() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        Medico medico = medico();
        Consultorio enUso = consultorio();
        horario(medico, enUso, LocalDate.now().plusDays(3));

        mockMvc.perform(delete("/api/consultorios/" + enUso.getId()).header("Authorization", token(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void una_ruta_que_no_existe_da_404_con_el_json_de_la_api() throws Exception {
        mockMvc.perform(get("/api/esto-no-existe").header("Authorization", token(usuario(Rol.ADMIN))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void un_metodo_no_permitido_da_405_con_el_json_de_la_api() throws Exception {
        mockMvc.perform(delete("/api/medicos").header("Authorization", token(usuario(Rol.ADMIN))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void un_json_mal_formado_da_400_con_el_json_de_la_api() throws Exception {
        mockMvc.perform(post("/api/turnos").header("Authorization", token(usuario(Rol.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content("{{{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo de la solicitud no es válido"));
    }

    @Test
    void una_fecha_con_formato_invalido_da_400_y_nombra_el_parametro() throws Exception {
        Medico medico = medico();

        mockMvc.perform(get("/api/medicos/" + medico.getId() + "/disponibilidad").param("fecha", "no-es-fecha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("fecha")));
    }

    @Test
    void ningun_error_del_servidor_incluye_el_sql_ni_el_stack() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        Consultorio enUso = consultorio();
        horario(medico(), enUso, LocalDate.now().plusDays(3));

        String cuerpo = mockMvc.perform(delete("/api/consultorios/" + enUso.getId()).header("Authorization", token(admin)))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo)
                .doesNotContain("constraint", "SQL", "Exception", "horario_atencion", "at com.");
    }
}
