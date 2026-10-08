package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que se decidió con la clínica: un recepcionista que atiende el mostrador (sin ver historias
 * clínicas), feriados y ausencias cargados por anticipado, 20 minutos de tolerancia antes de dar a
 * un paciente por ausente y no más de 2 turnos pendientes por paciente. Todo por HTTP y con commits
 * reales, porque el tope de turnos se tiene que sostener también cuando los pedidos llegan juntos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class RecepcionYCierresIntegrationTest extends BaseIntegracionConDatos {

    private static final LocalDate FECHA = LocalDate.now().plusDays(4);

    private LocalDateTime slot(int indice) {
        return LocalDateTime.of(FECHA, LocalTime.of(8, 0).plusMinutes(30L * indice));
    }

    private Medico medicoConAgenda() {
        Medico medico = medico();
        horario(medico, consultorio(), FECHA);
        return medico;
    }

    private String json(String... paresClaveValor) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(paresClaveValor[i]).append("\":").append(paresClaveValor[i + 1]);
        }
        return sb.append('}').toString();
    }

    private String texto(Object valor) {
        return '"' + String.valueOf(valor) + '"';
    }

    private Long cargarCierre(Usuario quien, LocalDate desde, LocalDate hasta, Medico medicoOMasNull) throws Exception {
        String cuerpo = json("desde", texto(desde), "hasta", texto(hasta), "descripcion", texto(PREFIJO + "Feriado de prueba"),
                "medicoId", medicoOMasNull == null ? "null" : String.valueOf(medicoOMasNull.getId()));
        String respuesta = mockMvc.perform(post("/api/dias-no-laborables").header("Authorization", token(quien))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(respuesta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    // --- el rol RECEPCIONISTA ---

    @Test
    void la_recepcion_maneja_el_mostrador_pero_no_entra_a_la_administracion_ni_a_las_historias_clinicas() throws Exception {
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        String auth = token(recepcion);

        // lo que SI hace
        mockMvc.perform(get("/api/pacientes/pendientes").header("Authorization", auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/dias-no-laborables").header("Authorization", auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/turnos/agenda").param("medicoId", String.valueOf(medico.getId())).header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/turnos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json("medicoId", String.valueOf(medico.getId()), "inicio", texto(slot(0)), "pacienteDni", texto(paciente.getDni()))))
                .andExpect(status().isCreated());

        // lo que NO
        for (String url : List.of("/api/usuarios", "/api/metricas", "/api/auditoria/accesos-historial")) {
            mockMvc.perform(get(url).header("Authorization", auth)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/historial/paciente/" + paciente.getId()).header("Authorization", auth))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceso_historial WHERE paciente_id = ?", Long.class, paciente.getId()))
                .as("un acceso denegado no deja un registro de lectura").isZero();
    }

    @Test
    void la_recepcion_no_puede_cargar_consultas_ni_ver_las_de_un_turno_atendido() throws Exception {
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        Paciente paciente = paciente();
        LocalDateTime inicio = LocalDateTime.now().minusDays(1);
        Turno atendido = turnoRepository.saveAndFlush(Turno.builder().medico(medico()).paciente(paciente).consultorio(consultorio())
                .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.ATENDIDO).build());
        consultaRepository.saveAndFlush(Consulta.builder().turno(atendido).notas("confidencial").build());

        mockMvc.perform(get("/api/turnos/" + atendido.getId() + "/consulta").header("Authorization", token(recepcion)))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_paciente_y_un_medico_no_cargan_feriados() throws Exception {
        String cuerpo = json("desde", texto(FECHA), "hasta", texto(FECHA), "descripcion", texto(PREFIJO + "x"));
        for (Usuario quien : List.of(paciente().getUsuario(), medico().getUsuario())) {
            mockMvc.perform(post("/api/dias-no-laborables").header("Authorization", token(quien))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/dias-no-laborables").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void el_admin_da_de_alta_una_recepcionista_que_despues_puede_entrar_al_mostrador() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        String email = "mostrador-" + UUID.randomUUID().toString().substring(0, 8) + DOMINIO;

        mockMvc.perform(post("/api/usuarios").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("rol", texto("RECEPCIONISTA"), "nombre", texto("QA Rita"), "apellido", texto("Recepcion"),
                                "email", texto(email), "password", texto("clave-de-prueba-1234"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("RECEPCIONISTA"));

        String token = mockMvc.perform(post("/api/auth/login").with(desde("10.9.9.1")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", texto(email), "password", texto("clave-de-prueba-1234"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/pacientes/pendientes").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    // --- feriados y ausencias ---

    @Test
    void un_feriado_cargado_por_anticipado_cierra_la_agenda_de_todos_los_medicos_ese_dia() throws Exception {
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        String disponibilidad = "/api/medicos/" + medico.getId() + "/disponibilidad";

        mockMvc.perform(get(disponibilidad).param("fecha", FECHA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.horariosLibres.length()").value(24));

        Long cierre = cargarCierre(recepcion, FECHA, FECHA, null);

        mockMvc.perform(get(disponibilidad).param("fecha", FECHA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.horariosLibres", hasSize(0)))
                .andExpect(jsonPath("$.aviso").value(containsString("Feriado de prueba")));
        mockMvc.perform(get(disponibilidad).param("fecha", FECHA.plusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aviso").doesNotExist());
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(0)))).isEqualTo(400);

        // si se carga por error, se borra y la agenda vuelve a abrirse
        mockMvc.perform(delete("/api/dias-no-laborables/" + cierre).header("Authorization", token(recepcion)))
                .andExpect(status().isNoContent());
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(0)))).isEqualTo(201);
    }

    @Test
    void la_ausencia_de_un_medico_solo_cierra_su_agenda_y_no_le_cuenta_el_motivo_al_paciente() throws Exception {
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        Medico enfermo = medicoConAgenda();
        Medico sano = medicoConAgenda();
        Paciente paciente = paciente();

        cargarCierre(recepcion, FECHA, FECHA.plusDays(2), enfermo);

        assertThat(estado(reservar(paciente.getUsuario(), enfermo, slot(0)))).isEqualTo(400);
        assertThat(estado(reservar(paciente.getUsuario(), sano, slot(0)))).isEqualTo(201);
        mockMvc.perform(get("/api/medicos/" + enfermo.getId() + "/disponibilidad").param("fecha", FECHA.toString()))
                .andExpect(jsonPath("$.aviso").value("El médico no atiende ese día"));
    }

    @Test
    void cargar_un_cierre_con_turnos_ya_reservados_no_los_cancela_pero_los_lista_con_el_telefono_para_avisar() throws Exception {
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        jdbc.update("UPDATE usuario SET telefono = '261-555-0101' WHERE id = ?", paciente.getUsuario().getId());
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(2)))).isEqualTo(201);

        Long cierre = cargarCierre(recepcion, FECHA, FECHA, medico);

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE medico_id = ?", String.class, medico.getId()))
                .isEqualTo("RESERVADO");
        mockMvc.perform(get("/api/dias-no-laborables/" + cierre + "/turnos-afectados").header("Authorization", token(recepcion)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pacienteTelefono").value("261-555-0101"))
                .andExpect(jsonPath("$[0].estado").value("RESERVADO"));

        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());
        mockMvc.perform(post("/api/dias-no-laborables/" + cierre + "/cancelar-turnos").header("Authorization", token(recepcion))
                        .contentType(MediaType.APPLICATION_JSON).content(json("motivoCancelacionId", String.valueOf(motivo.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelados").value(1));

        assertThat(jdbc.queryForMap("SELECT estado, motivo_cancelacion_id FROM turno WHERE medico_id = ?", medico.getId()))
                .containsEntry("estado", "CANCELADO").containsEntry("motivo_cancelacion_id", motivo.getId());
        mockMvc.perform(get("/api/dias-no-laborables/" + cierre + "/turnos-afectados").header("Authorization", token(recepcion)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void el_cancelado_en_bloque_solo_toca_los_turnos_del_periodo_y_del_medico_afectado() throws Exception {
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        Medico enfermo = medicoConAgenda();
        Medico sano = medicoConAgenda();
        Paciente a = paciente();
        Paciente b = paciente();
        assertThat(estado(reservar(a.getUsuario(), enfermo, slot(0)))).isEqualTo(201);
        assertThat(estado(reservar(b.getUsuario(), sano, slot(0)))).isEqualTo(201);
        Long cierre = cargarCierre(recepcion, FECHA, FECHA, enfermo);
        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());

        mockMvc.perform(post("/api/dias-no-laborables/" + cierre + "/cancelar-turnos").header("Authorization", token(recepcion))
                        .contentType(MediaType.APPLICATION_JSON).content(json("motivoCancelacionId", String.valueOf(motivo.getId()))))
                .andExpect(jsonPath("$.cancelados").value(1));

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE medico_id = ?", String.class, sano.getId())).isEqualTo("RESERVADO");
    }

    @Test
    void los_periodos_mal_cargados_se_rechazan() throws Exception {
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        String auth = token(recepcion);

        // la fecha final antes de la inicial
        postCierre(auth, FECHA.toString(), FECHA.minusDays(1).toString(), PREFIJO + "x").andExpect(status().isBadRequest());
        // un periodo que ya termino
        postCierre(auth, LocalDate.now().minusDays(5).toString(), LocalDate.now().minusDays(2).toString(), PREFIJO + "x")
                .andExpect(status().isBadRequest());
        // mas de un anio: casi seguro un error de carga
        postCierre(auth, FECHA.toString(), FECHA.plusDays(400).toString(), PREFIJO + "x").andExpect(status().isBadRequest());
        // sin descripcion
        postCierre(auth, FECHA.toString(), FECHA.toString(), "  ").andExpect(status().isBadRequest());
        // medico inexistente
        mockMvc.perform(post("/api/dias-no-laborables").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json("desde", texto(FECHA), "hasta", texto(FECHA), "descripcion", texto(PREFIJO + "x"), "medicoId", "999999999")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/dias-no-laborables/999999999").header("Authorization", auth)).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions postCierre(String auth, String desde, String hasta, String descripcion) throws Exception {
        return mockMvc.perform(post("/api/dias-no-laborables").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(json("desde", texto(desde), "hasta", texto(hasta), "descripcion", texto(descripcion))));
    }

    // --- tolerancia de 20 minutos ---

    @Test
    void no_se_puede_dar_por_ausente_a_un_paciente_antes_de_los_20_minutos_y_si_despues() throws Exception {
        Medico medico = medico();
        Paciente paciente = paciente();
        String auth = token(medico.getUsuario());

        Turno reciente = turnoCon(medico, paciente, LocalDateTime.now().minusMinutes(10), EstadoTurno.CONFIRMADO);
        mockMvc.perform(patch("/api/turnos/" + reciente.getId() + "/marcar-ausente").header("Authorization", auth))
                .andExpect(status().isBadRequest());

        Turno vencido = turnoCon(medico, paciente, LocalDateTime.now().minusMinutes(90), EstadoTurno.CONFIRMADO);
        mockMvc.perform(patch("/api/turnos/" + vencido.getId() + "/marcar-ausente").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("AUSENTE"));
    }

    private Turno turnoCon(Medico medico, Paciente paciente, LocalDateTime inicio, EstadoTurno estado) {
        return turnoRepository.saveAndFlush(Turno.builder().medico(medico).paciente(paciente).consultorio(consultorio())
                .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(estado).build());
    }

    // --- tope de 2 turnos pendientes ---

    @Test
    void un_paciente_no_puede_tener_un_tercer_turno_pendiente_pero_si_cuando_cancela_uno() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);

        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(0)))).isEqualTo(201);
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(1)))).isEqualTo(201);
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(2)))).isEqualTo(409);

        // el tope vale tambien para quien reserva en el mostrador a nombre del paciente
        mockMvc.perform(post("/api/turnos").header("Authorization", token(recepcion)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("medicoId", String.valueOf(medico.getId()), "inicio", texto(slot(2)), "pacienteDni", texto(paciente.getDni()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(containsString("Ese paciente ya tiene 2 turnos pendientes")));

        // cancelar uno libera el cupo
        Long primero = turnoRepository.findByPacienteIdOrderByInicioDesc(paciente.getId()).get(1).getId();
        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());
        mockMvc.perform(patch("/api/turnos/" + primero + "/cancelar").header("Authorization", token(paciente.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(json("motivoCancelacionId", String.valueOf(motivo.getId()))))
                .andExpect(status().isOk());
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(2)))).isEqualTo(201);
    }

    @Test
    void los_turnos_ya_atendidos_o_pasados_no_cuentan_contra_el_tope() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        turnoCon(medico, paciente, ayer, EstadoTurno.ATENDIDO);
        turnoCon(medico, paciente, ayer.minusDays(1), EstadoTurno.AUSENTE);
        turnoCon(medico, paciente, ayer.minusDays(2), EstadoTurno.RESERVADO);   // vencido y nunca resuelto: tampoco frena

        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(0)))).isEqualTo(201);
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(1)))).isEqualTo(201);
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(2)))).isEqualTo(409);
    }

    @Test
    void cinco_reservas_simultaneas_del_mismo_paciente_terminan_en_exactamente_2_turnos() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();

        for (int ronda = 0; ronda < 4; ronda++) {
            List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> pedidos = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                pedidos.add(reservar(paciente.getUsuario(), medico, slot(ronda * 5 + i)));
            }
            int[] resultados = enParalelo(pedidos);

            assertThat(Arrays.stream(resultados).filter(r -> r == 201).count()).as("ronda %d: %s", ronda, Arrays.toString(resultados)).isEqualTo(2);
            assertThat(Arrays.stream(resultados).filter(r -> r == 409).count()).as("ronda %d: %s", ronda, Arrays.toString(resultados)).isEqualTo(3);
            assertThat(turnoRepository.countByPacienteIdAndEstadoInAndFinAfter(paciente.getId(),
                    List.of(EstadoTurno.RESERVADO, EstadoTurno.CONFIRMADO), LocalDateTime.now())).as("ronda %d", ronda).isEqualTo(2);

            jdbc.update("UPDATE turno SET estado = 'ATENDIDO' WHERE paciente_id = ?", paciente.getId());   // libera el cupo para la ronda siguiente
        }
    }
}
