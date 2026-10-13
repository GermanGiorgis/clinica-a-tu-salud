package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Próximo día con horarios": quien abre la agenda un día en que el médico no atiende no tiene que adivinar cuándo sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class ProximoDiaIntegrationTest extends BaseIntegracionConDatos {

    /** Un día de atención del médico: dentro de 5 días; el resto de los días de la semana no atiende. */
    private static final LocalDate DIA = LocalDate.now().plusDays(5);

    private Medico medicoQueAtiendeSoloEseDiaDeLaSemana() {
        Medico medico = medico();
        horario(medico, consultorio(), DIA);
        return medico;
    }

    private ResultActions proximo(Medico medico, LocalDate desde) throws Exception {
        var pedido = get("/api/medicos/" + medico.getId() + "/proximo-dia-disponible");
        return mockMvc.perform(desde == null ? pedido : pedido.param("desde", desde.toString()));
    }

    @Test
    void desde_un_dia_sin_atencion_devuelve_el_siguiente_dia_en_que_atiende_sin_iniciar_sesion() throws Exception {
        Medico medico = medicoQueAtiendeSoloEseDiaDeLaSemana();

        proximo(medico, DIA.minusDays(2)).andExpect(status().isOk()).andExpect(jsonPath("$.fecha").value(DIA.toString()));
    }

    @Test
    void sin_fecha_de_partida_busca_desde_hoy() throws Exception {
        Medico medico = medicoQueAtiendeSoloEseDiaDeLaSemana();

        proximo(medico, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void si_la_fecha_ya_tiene_horarios_devuelve_esa_misma() throws Exception {
        Medico medico = medicoQueAtiendeSoloEseDiaDeLaSemana();

        proximo(medico, DIA).andExpect(jsonPath("$.fecha").value(DIA.toString()));
    }

    @Test
    void una_fecha_de_partida_pasada_se_toma_como_hoy_y_no_da_error() throws Exception {
        Medico medico = medicoQueAtiendeSoloEseDiaDeLaSemana();

        proximo(medico, LocalDate.now().minusDays(30)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void un_medico_sin_horarios_no_tiene_proximo_dia() throws Exception {
        proximo(medico(), null).andExpect(status().isOk()).andExpect(jsonPath("$.fecha").doesNotExist());
    }

    @Test
    void salta_los_dias_cerrados_por_un_feriado() throws Exception {
        Medico medico = medicoQueAtiendeSoloEseDiaDeLaSemana();
        String cierre = "{\"desde\":\"" + DIA + "\",\"hasta\":\"" + DIA + "\",\"descripcion\":\"" + PREFIJO + "Feriado\",\"medicoId\":null}";
        mockMvc.perform(post("/api/dias-no-laborables").header("Authorization", token(usuario(Rol.RECEPCIONISTA)))
                .contentType(MediaType.APPLICATION_JSON).content(cierre)).andExpect(status().isCreated());

        // el mismo día de la semana, una semana después
        proximo(medico, DIA.minusDays(2)).andExpect(jsonPath("$.fecha").value(DIA.plusDays(7).toString()));
    }

    @Test
    void salta_los_dias_en_que_ya_no_queda_ningun_horario() throws Exception {
        Medico medico = medicoQueAtiendeSoloEseDiaDeLaSemana();
        // El horario va de 08:00 a 20:00 en turnos de 30 minutos: 24 turnos. Cada paciente puede tener 2 pendientes.
        for (int paciente = 0; paciente < 12; paciente++) {
            var quien = paciente().getUsuario();
            for (int i = 0; i < 2; i++) {
                LocalTime hora = LocalTime.of(8, 0).plusMinutes(30L * (paciente * 2 + i));
                mockMvc.perform(reservar(quien, medico, LocalDateTime.of(DIA, hora))).andExpect(status().isCreated());
            }
        }

        proximo(medico, DIA).andExpect(jsonPath("$.fecha").value(DIA.plusDays(7).toString()));
    }

    @Test
    void un_medico_inexistente_es_un_404() throws Exception {
        mockMvc.perform(get("/api/medicos/999999999/proximo-dia-disponible")).andExpect(status().isNotFound());
    }

    @Test
    void un_medico_dado_de_baja_no_tiene_proximo_dia() throws Exception {
        Medico medico = medicoQueAtiendeSoloEseDiaDeLaSemana();
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", medico.getUsuario().getId());

        proximo(medico, null).andExpect(status().isOk()).andExpect(jsonPath("$.fecha").doesNotExist());
    }
}
