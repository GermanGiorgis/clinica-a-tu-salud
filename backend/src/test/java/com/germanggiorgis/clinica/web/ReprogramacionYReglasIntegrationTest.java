package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que apareció al usar la aplicación a mano: reprogramar un turno, médicos desactivados que todavía se
 * podían reservar, atender un turno de dentro de tres días, DNIs y fechas sin sentido, afiliaciones repetidas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class ReprogramacionYReglasIntegrationTest extends BaseIntegracionConDatos {

    private static final LocalDate FECHA = LocalDate.now().plusDays(5);

    private LocalDateTime slot(int indice) {
        return LocalDateTime.of(FECHA, LocalTime.of(8, 0).plusMinutes(30L * indice));
    }

    private Medico medicoConAgenda() {
        Medico medico = medico();
        horario(medico, consultorio(), FECHA);
        return medico;
    }

    private MockHttpServletRequestBuilder reprogramar(Usuario quien, Long turnoId, LocalDateTime nuevoInicio) {
        return patch("/api/turnos/" + turnoId + "/reprogramar").header("Authorization", token(quien))
                .contentType(MediaType.APPLICATION_JSON).content("{\"inicio\":\"" + nuevoInicio + "\"}");
    }

    private Long turnoReservado(Usuario paciente, Medico medico, LocalDateTime inicio) throws Exception {
        mockMvc.perform(reservar(paciente, medico, inicio)).andExpect(status().isCreated());
        return turnoRepository.findByMedicoIdAndInicioBetween(medico.getId(), inicio.minusMinutes(1), inicio.plusMinutes(1)).get(0).getId();
    }

    private Turno turnoDirecto(Medico medico, Paciente paciente, LocalDateTime inicio, EstadoTurno estado) {
        return turnoRepository.saveAndFlush(Turno.builder().medico(medico).paciente(paciente).consultorio(consultorio())
                .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(estado).build());
    }

    // --- reprogramar ---

    @Test
    void el_paciente_reprograma_su_turno_a_otro_horario_y_el_viejo_queda_libre() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Long id = turnoReservado(paciente.getUsuario(), medico, slot(2));
        mockMvc.perform(patch("/api/turnos/" + id + "/confirmar").header("Authorization", token(paciente.getUsuario()))).andExpect(status().isOk());

        mockMvc.perform(reprogramar(paciente.getUsuario(), id, slot(6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.inicio").value(slot(6) + ":00"))
                .andExpect(jsonPath("$.estado").value("RESERVADO"));

        mockMvc.perform(get("/api/medicos/" + medico.getId() + "/disponibilidad").param("fecha", FECHA.toString()))
                .andExpect(jsonPath("$.horariosLibres", hasItem("09:00:00")))        // slot(2): el viejo, libre
                .andExpect(jsonPath("$.horariosLibres", not(hasItem("11:00:00"))));   // slot(6): el nuevo, ocupado
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void reprogramar_no_cuenta_contra_el_tope_de_dos_turnos() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Long primero = turnoReservado(paciente.getUsuario(), medico, slot(0));
        turnoReservado(paciente.getUsuario(), medico, slot(1));
        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(2)))).isEqualTo(409);

        mockMvc.perform(reprogramar(paciente.getUsuario(), primero, slot(8))).andExpect(status().isOk());
    }

    @Test
    void no_se_puede_reprogramar_al_horario_de_otro_ni_al_propio_ni_a_uno_fuera_de_agenda() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente a = paciente();
        Paciente b = paciente();
        Long idA = turnoReservado(a.getUsuario(), medico, slot(0));
        turnoReservado(b.getUsuario(), medico, slot(3));

        mockMvc.perform(reprogramar(a.getUsuario(), idA, slot(3))).andExpect(status().isConflict());
        mockMvc.perform(reprogramar(a.getUsuario(), idA, slot(0))).andExpect(status().isBadRequest());
        mockMvc.perform(reprogramar(a.getUsuario(), idA, LocalDateTime.of(FECHA, LocalTime.of(3, 0)))).andExpect(status().isBadRequest());
        mockMvc.perform(reprogramar(a.getUsuario(), idA, slot(0).minusDays(30))).andExpect(status().isBadRequest());
        mockMvc.perform(reprogramar(a.getUsuario(), idA, slot(0).plusDays(100))).andExpect(status().isBadRequest());
        assertThat(turnoRepository.findById(idA).orElseThrow().getInicio()).isEqualTo(slot(0));
    }

    @Test
    void solo_el_dueno_el_medico_y_el_mostrador_pueden_reprogramar() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Long id = turnoReservado(paciente.getUsuario(), medico, slot(0));

        mockMvc.perform(reprogramar(paciente().getUsuario(), id, slot(5))).andExpect(status().isForbidden());
        mockMvc.perform(reprogramar(medico().getUsuario(), id, slot(5))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        mockMvc.perform(reprogramar(medico.getUsuario(), id, slot(5))).andExpect(status().isOk());
        mockMvc.perform(reprogramar(usuario(Rol.RECEPCIONISTA), id, slot(6))).andExpect(status().isOk());
        mockMvc.perform(reprogramar(usuario(Rol.ADMIN), id, slot(7))).andExpect(status().isOk());
    }

    @Test
    void un_turno_atendido_cancelado_o_ausente_no_se_reprograma() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);
        int indice = 12;
        for (EstadoTurno estado : new EstadoTurno[]{EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE}) {
            // futuros y con el horario nuevo libre: lo único que lo puede frenar es el estado (la recepción no tiene el tope de 2 horas)
            Turno turno = turnoDirecto(medico, paciente, slot(indice++), estado);
            mockMvc.perform(reprogramar(recepcion, turno.getId(), slot(indice++))).andExpect(status().isBadRequest());
            mockMvc.perform(reprogramar(paciente.getUsuario(), turno.getId(), slot(indice++))).andExpect(status().isBadRequest());
            assertThat(turnoRepository.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(estado);
        }
    }

    @Test
    void el_paciente_no_reprograma_dentro_de_las_dos_horas_previas_pero_la_recepcion_si() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Turno inminente = turnoDirecto(medico, paciente, LocalDateTime.now().plusMinutes(60), EstadoTurno.RESERVADO);

        mockMvc.perform(reprogramar(paciente.getUsuario(), inminente.getId(), slot(9))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("2 horas")));
        mockMvc.perform(reprogramar(usuario(Rol.RECEPCIONISTA), inminente.getId(), slot(9))).andExpect(status().isOk());
    }

    @Test
    void dos_reprogramaciones_simultaneas_al_mismo_horario_dejan_una_sola_ganadora() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente a = paciente();
        Paciente b = paciente();
        Long idA = turnoReservado(a.getUsuario(), medico, slot(0));
        Long idB = turnoReservado(b.getUsuario(), medico, slot(1));

        for (int ronda = 0; ronda < 4; ronda++) {
            int destino = 10 + ronda;
            int[] r = enParalelo(reprogramar(a.getUsuario(), idA, slot(destino)), reprogramar(b.getUsuario(), idB, slot(destino)));
            assertThat(ordenados(r)).as("ronda %d: %s", ronda, java.util.Arrays.toString(r)).containsExactly(200, 409);
            // la que perdió vuelve a uno libre para la ronda siguiente
            Long perdedor = r[0] == 409 ? idA : idB;
            Usuario quien = r[0] == 409 ? a.getUsuario() : b.getUsuario();
            mockMvc.perform(reprogramar(quien, perdedor, slot(20 + ronda))).andExpect(status().isOk());
            jdbc.update("UPDATE turno SET inicio = ?, fin = ? WHERE id = ?", slot(30 + ronda * 2), slot(30 + ronda * 2).plusMinutes(30), idA);
            jdbc.update("UPDATE turno SET inicio = ?, fin = ? WHERE id = ?", slot(31 + ronda * 2), slot(31 + ronda * 2).plusMinutes(30), idB);
        }
        // nunca quedaron dos turnos activos del médico a la misma hora
        Long repetidos = jdbc.queryForObject("SELECT count(*) FROM (SELECT inicio FROM turno WHERE medico_id = ? AND estado <> 'CANCELADO' GROUP BY inicio HAVING count(*) > 1) x",
                Long.class, medico.getId());
        assertThat(repetidos).isZero();
    }

    // --- médico desactivado ---

    @Test
    void un_medico_desactivado_desaparece_del_publico_no_se_le_puede_reservar_pero_el_mostrador_lo_sigue_viendo() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Long id = turnoReservado(paciente.getUsuario(), medico, slot(0));
        Usuario admin = usuario(Rol.ADMIN);
        Usuario recepcion = usuario(Rol.RECEPCIONISTA);

        mockMvc.perform(patch("/api/usuarios/" + medico.getUsuario().getId() + "/activo").header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/medicos")).andExpect(jsonPath("$[*].id", not(hasItem(medico.getId().intValue()))));
        mockMvc.perform(get("/api/medicos").param("incluirInactivos", "true"))
                .andExpect(jsonPath("$[*].id", not(hasItem(medico.getId().intValue()))));          // sin sesión: se ignora
        mockMvc.perform(get("/api/medicos").param("incluirInactivos", "true").header("Authorization", token(paciente.getUsuario())))
                .andExpect(jsonPath("$[*].id", not(hasItem(medico.getId().intValue()))));          // un paciente: se ignora
        mockMvc.perform(get("/api/medicos").param("incluirInactivos", "true").header("Authorization", token(recepcion)))
                .andExpect(jsonPath("$[*].id", hasItem(medico.getId().intValue())));                // el mostrador sí
        mockMvc.perform(get("/api/medicos/" + medico.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/medicos/" + medico.getId()).header("Authorization", token(recepcion)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(get("/api/medicos/" + medico.getId() + "/disponibilidad").param("fecha", FECHA.toString()))
                .andExpect(jsonPath("$.horariosLibres.length()").value(0))
                .andExpect(jsonPath("$.aviso").value(containsString("ya no atiende")));
        assertThat(estado(reservar(paciente().getUsuario(), medico, slot(4)))).isEqualTo(400);
        mockMvc.perform(reprogramar(paciente.getUsuario(), id, slot(5))).andExpect(status().isBadRequest());

        // su agenda sigue accesible para el mostrador: se pueden cancelar los turnos que le quedaron
        mockMvc.perform(get("/api/turnos/agenda").param("medicoId", medico.getId().toString()).header("Authorization", token(recepcion)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
    }

    // --- atender un turno que todavía no es ---

    @Test
    void el_medico_no_puede_cargar_la_consulta_de_un_turno_de_otro_dia_pero_si_de_hoy() throws Exception {
        Medico medico = medicoConAgenda();
        Paciente paciente = paciente();
        Turno futuro = turnoDirecto(medico, paciente, slot(0), EstadoTurno.CONFIRMADO);
        Turno deHoy = turnoDirecto(medico, paciente, LocalDate.now().atTime(23, 0), EstadoTurno.CONFIRMADO);
        String cuerpo = "{\"notas\":\"x\",\"diagnosticos\":[{\"descripcion\":\"Control\"}]}";

        mockMvc.perform(post("/api/turnos/" + futuro.getId() + "/consulta").header("Authorization", token(medico.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(containsString("Todavía no es el día")));
        assertThat(turnoRepository.findById(futuro.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);

        mockMvc.perform(post("/api/turnos/" + deHoy.getId() + "/consulta").header("Authorization", token(medico.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
        assertThat(consultaRepository.findAll().stream().map(Consulta::getTurno).map(Turno::getId)).contains(deHoy.getId()).doesNotContain(futuro.getId());
    }

    // --- datos sin sentido ---

    @Test
    void el_admin_no_puede_crear_un_paciente_con_fecha_de_nacimiento_futura_ni_con_un_dni_que_no_es_un_numero() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        String base = "{\"rol\":\"PACIENTE\",\"nombre\":\"QA Raro\",\"apellido\":\"Datos\",\"email\":\"raro-%s" + DOMINIO + "\","
                + "\"password\":\"clave-de-prueba-1234\",\"dni\":\"%s\",\"fechaNacimiento\":\"%s\"}";
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        for (String[] caso : new String[][]{{"30111222", "2999-01-01"}, {"ABC-123", "1990-01-01"}, {"123", "1990-01-01"},
                {"30.111.222", "1990-01-01"}, {"1234567890", "1990-01-01"}, {"3011 1222", "1990-01-01"}}) {
            mockMvc.perform(post("/api/usuarios").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(base, sufijo, caso[0], caso[1])))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/usuarios").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(base, sufijo, "30111222", "1990-01-01")))
                .andExpect(status().isCreated());
    }

    @Test
    void reservar_por_dni_rechaza_un_dni_con_letras_en_vez_de_buscarlo() throws Exception {
        Medico medico = medicoConAgenda();
        mockMvc.perform(post("/api/turnos").header("Authorization", token(usuario(Rol.RECEPCIONISTA))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicoId\":" + medico.getId() + ",\"inicio\":\"" + slot(0) + "\",\"pacienteDni\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- afiliaciones ---

    @Test
    void una_afiliacion_repetida_da_409_y_una_vigencia_al_reves_da_400() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        Paciente paciente = paciente();
        String nombre = PREFIJO + "OS " + UUID.randomUUID().toString().substring(0, 6);
        String respuesta = mockMvc.perform(post("/api/obras-sociales").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String osId = respuesta.replaceAll(".*\"id\":(\\d+).*", "$1");
        String alta = "{\"obraSocialId\":" + osId + ",\"numeroAfiliado\":\"000777\",\"vigenteDesde\":\"" + LocalDate.now() + "\"}";
        String alReves = "{\"obraSocialId\":" + osId + ",\"numeroAfiliado\":\"000778\",\"vigenteDesde\":\"" + LocalDate.now() + "\",\"vigenteHasta\":\"" + LocalDate.now().minusDays(3) + "\"}";

        try {
            mockMvc.perform(post("/api/afiliaciones").header("Authorization", token(paciente.getUsuario())).contentType(MediaType.APPLICATION_JSON).content(alta))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/afiliaciones").header("Authorization", token(paciente.getUsuario())).contentType(MediaType.APPLICATION_JSON).content(alta))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Ya cargaste esa afiliación"));
            mockMvc.perform(post("/api/afiliaciones").header("Authorization", token(paciente.getUsuario())).contentType(MediaType.APPLICATION_JSON).content(alReves))
                    .andExpect(status().isBadRequest());
            // otro paciente sí puede tener el mismo número en la misma obra social (titular y adherente, por ejemplo)
            mockMvc.perform(post("/api/afiliaciones").header("Authorization", token(paciente().getUsuario())).contentType(MediaType.APPLICATION_JSON).content(alta))
                    .andExpect(status().isCreated());
        } finally {
            jdbc.update("DELETE FROM afiliacion WHERE obra_social_id = ?", Long.valueOf(osId));
            jdbc.update("DELETE FROM obra_social WHERE id = ?", Long.valueOf(osId));
        }
    }

    // --- catálogo de motivos ---

    @Test
    void hay_motivos_de_cancelacion_basicos_para_elegir() throws Exception {
        mockMvc.perform(get("/api/motivos-cancelacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem("Médico no disponible")))
                .andExpect(jsonPath("$[*].nombre", hasItem("Feriado o cierre de la clínica")))
                .andExpect(jsonPath("$[*].nombre", hasItem("El paciente no puede asistir")));
    }
}
