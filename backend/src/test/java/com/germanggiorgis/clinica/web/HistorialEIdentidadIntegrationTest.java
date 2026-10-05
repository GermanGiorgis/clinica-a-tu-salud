package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consulta;
import com.germanggiorgis.clinica.model.Consultorio;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quién puede leer la historia clínica de quién, la verificación de identidad que la habilita y el
 * registro de accesos. Son los hallazgos críticos de la auditoría QA/seguridad, reproducidos por HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class HistorialEIdentidadIntegrationTest extends BaseIntegracionConDatos {

    private static final LocalDate FECHA = LocalDate.now().plusDays(3);
    private static final String NOTA_SECRETA = "NOTA CONFIDENCIAL de otro medico";

    /** Un paciente con una consulta ya atendida por OTRO médico, cuyas notas no debería ver cualquiera. */
    private Paciente pacienteConHistorialDeOtroMedico() {
        Paciente victima = paciente();
        LocalDateTime inicio = LocalDateTime.now().minusDays(10);
        Turno atendido = turnoRepository.saveAndFlush(Turno.builder().medico(medico()).paciente(victima).consultorio(consultorio())
                .creadoPor(victima.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.ATENDIDO).build());
        consultaRepository.saveAndFlush(Consulta.builder().turno(atendido).notas(NOTA_SECRETA).build());
        return victima;
    }

    private Medico medicoConAgenda() {
        Medico medico = medico();
        horario(medico, consultorio(), FECHA);
        return medico;
    }

    private String historialDe(Paciente paciente, Usuario quien, int estadoEsperado) throws Exception {
        return mockMvc.perform(get("/api/historial/paciente/" + paciente.getId()).header("Authorization", token(quien)))
                .andExpect(status().is(estadoEsperado)).andReturn().getResponse().getContentAsString();
    }

    private Long reservarPorDni(Usuario medico, Medico agenda, Paciente paciente) throws Exception {
        mockMvc.perform(post("/api/turnos").header("Authorization", token(medico)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicoId\":" + agenda.getId() + ",\"inicio\":\"" + LocalDateTime.of(FECHA, LocalTime.of(9, 0))
                        + "\",\"pacienteDni\":\"" + paciente.getDni() + "\"}")).andExpect(status().isCreated());
        return turnoRepository.findByPacienteIdOrderByInicioDesc(paciente.getId()).stream()
                .filter(t -> t.getMedico().getId().equals(agenda.getId())).findFirst().orElseThrow().getId();
    }

    // --- el ataque de la auditoría: reservarle un turno a la víctima para leer su historial ---

    @Test
    void un_medico_no_puede_leer_el_historial_de_un_paciente_reservandole_un_turno_el_mismo() throws Exception {
        Paciente victima = pacienteConHistorialDeOtroMedico();
        Medico atacante = medicoConAgenda();
        historialDe(victima, atacante.getUsuario(), 403);

        Long turnoId = reservarPorDni(atacante.getUsuario(), atacante, victima);   // 201: puede reservar por DNI
        String cuerpo = historialDe(victima, atacante.getUsuario(), 403);          // ...pero eso no lo habilita

        assertThat(cuerpo).doesNotContain(NOTA_SECRETA);

        // Cancelar el turno tampoco cambia nada.
        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());
        mockMvc.perform(patch("/api/turnos/" + turnoId + "/cancelar").header("Authorization", token(atacante.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivoCancelacionId\":" + motivo.getId() + "}")).andExpect(status().isOk());
        historialDe(victima, atacante.getUsuario(), 403);

        // Los intentos rechazados no figuran como accesos: no se vio nada.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceso_historial WHERE paciente_id = ?", Integer.class, victima.getId())).isZero();
    }

    @Test
    void un_medico_si_puede_leerlo_cuando_es_el_propio_paciente_quien_reserva_con_el_y_queda_registrado() throws Exception {
        Paciente paciente = pacienteConHistorialDeOtroMedico();
        Medico medico = medicoConAgenda();
        mockMvc.perform(post("/api/turnos").header("Authorization", token(paciente.getUsuario())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicoId\":" + medico.getId() + ",\"inicio\":\"" + LocalDateTime.of(FECHA, LocalTime.of(9, 0)) + "\"}"))
                .andExpect(status().isCreated());

        String cuerpo = historialDe(paciente, medico.getUsuario(), 200);

        assertThat(cuerpo).contains(NOTA_SECRETA);

        // Y el admin ve quién lo miró.
        Usuario admin = usuario(Rol.ADMIN);
        mockMvc.perform(get("/api/auditoria/accesos-historial").param("pacienteId", String.valueOf(paciente.getId()))
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuarioEmail").value(medico.getUsuario().getEmail()))
                .andExpect(jsonPath("$[0].usuarioRol").value("MEDICO"))
                .andExpect(jsonPath("$[0].pacienteId").value(paciente.getId()));
    }

    @Test
    void cuando_el_paciente_cancela_ese_turno_el_medico_pierde_el_acceso() throws Exception {
        Paciente paciente = pacienteConHistorialDeOtroMedico();
        Medico medico = medicoConAgenda();
        mockMvc.perform(post("/api/turnos").header("Authorization", token(paciente.getUsuario())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicoId\":" + medico.getId() + ",\"inicio\":\"" + LocalDateTime.of(FECHA, LocalTime.of(9, 0)) + "\"}"))
                .andExpect(status().isCreated());
        historialDe(paciente, medico.getUsuario(), 200);

        Long turnoId = turnoRepository.findByPacienteIdOrderByInicioDesc(paciente.getId()).stream()
                .filter(t -> t.getMedico().getId().equals(medico.getId())).findFirst().orElseThrow().getId();
        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());
        mockMvc.perform(patch("/api/turnos/" + turnoId + "/cancelar").header("Authorization", token(paciente.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivoCancelacionId\":" + motivo.getId() + "}")).andExpect(status().isOk());

        historialDe(paciente, medico.getUsuario(), 403);
    }

    @Test
    void el_registro_de_accesos_solo_lo_ve_un_admin() throws Exception {
        mockMvc.perform(get("/api/auditoria/accesos-historial")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auditoria/accesos-historial").header("Authorization", token(usuario(Rol.MEDICO))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auditoria/accesos-historial").header("Authorization", token(paciente().getUsuario())))
                .andExpect(status().isForbidden());
    }

    // --- verificación de identidad ---

    @Test
    void una_reserva_por_dni_solo_funciona_con_pacientes_de_identidad_verificada() throws Exception {
        Paciente sinVerificar = paciente(false);
        Medico medico = medicoConAgenda();
        Usuario admin = usuario(Rol.ADMIN);
        String cuerpo = "{\"medicoId\":" + medico.getId() + ",\"inicio\":\"" + LocalDateTime.of(FECHA, LocalTime.of(9, 0))
                + "\",\"pacienteDni\":\"" + sinVerificar.getDni() + "\"}";

        mockMvc.perform(post("/api/turnos").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("verificada")));

        mockMvc.perform(patch("/api/pacientes/" + sinVerificar.getId() + "/verificar").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identidadVerificada").value(true));

        mockMvc.perform(post("/api/turnos").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
    }

    @Test
    void un_medico_no_ve_el_historial_de_un_paciente_sin_verificar_hasta_que_la_clinica_lo_verifica() throws Exception {
        Paciente paciente = paciente(false);
        Medico medico = medicoConAgenda();
        mockMvc.perform(post("/api/turnos").header("Authorization", token(paciente.getUsuario())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicoId\":" + medico.getId() + ",\"inicio\":\"" + LocalDateTime.of(FECHA, LocalTime.of(9, 0)) + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/historial/paciente/" + paciente.getId()).header("Authorization", token(medico.getUsuario())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("verific")));

        mockMvc.perform(patch("/api/pacientes/" + paciente.getId() + "/verificar").header("Authorization", token(usuario(Rol.ADMIN))))
                .andExpect(status().isOk());

        historialDe(paciente, medico.getUsuario(), 200);
    }

    @Test
    void cualquiera_puede_registrarse_con_un_dni_ajeno_sin_enterarse_de_si_existia_y_la_clinica_verifica_a_uno_solo() throws Exception {
        Paciente verdadero = paciente();   // ya verificado, con ese DNI
        Usuario admin = usuario(Rol.ADMIN);
        String emailImpostor = "impostor-" + UUID.randomUUID().toString().substring(0, 8) + DOMINIO;

        // El impostor se registra con el DNI de otra persona: se acepta igual, sin ningún aviso de "ese DNI ya existe".
        mockMvc.perform(post("/api/auth/registro").with(desde("10.50.0.1")).contentType(MediaType.APPLICATION_JSON)
                        .content(registro(emailImpostor, verdadero.getDni())))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("DNI"))));

        Paciente impostor = pacienteRepository.findByUsuarioId(usuarioRepository.findByEmailIgnoreCase(emailImpostor).orElseThrow().getId()).orElseThrow();
        assertThat(impostor.isIdentidadVerificada()).isFalse();

        // Queda pendiente para que el personal compare con el documento...
        mockMvc.perform(get("/api/pacientes/pendientes").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + impostor.getId() + ")].email").value(emailImpostor));

        // ...y no se lo puede verificar porque ese DNI ya es de otro paciente verificado.
        mockMvc.perform(patch("/api/pacientes/" + impostor.getId() + "/verificar").header("Authorization", token(admin)))
                .andExpect(status().isConflict());

        // El admin desactiva la cuenta: sale de la lista de pendientes y no puede entrar.
        mockMvc.perform(patch("/api/usuarios/" + impostor.getUsuario().getId() + "/activo").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pacientes/pendientes").header("Authorization", token(admin)))
                .andExpect(jsonPath("$[?(@.id==" + impostor.getId() + ")]").isEmpty());
    }

    @Test
    void verificar_y_ver_pendientes_son_solo_para_admin() throws Exception {
        Paciente paciente = paciente(false);

        mockMvc.perform(get("/api/pacientes/pendientes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pacientes/pendientes").header("Authorization", token(usuario(Rol.MEDICO)))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/pacientes/" + paciente.getId() + "/verificar").header("Authorization", token(paciente.getUsuario())))
                .andExpect(status().isForbidden());
    }

    private String registro(String email, String dni) {
        return "{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\",\"email\":\"" + email + "\",\"password\":\"clave-de-prueba-1234\","
                + "\"dni\":\"" + dni + "\",\"fechaNacimiento\":\"1990-01-01\"}";
    }
}
