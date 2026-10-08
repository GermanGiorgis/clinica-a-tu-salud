package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrencia REAL: dos hilos, dos transacciones, commits de verdad contra Postgres. Lo que
 * se prueba es que ninguna carrera deje la agenda inconsistente ni pise una acción en
 * silencio. Los tests con {@code @Transactional} no pueden ver esto (todo corre en una
 * sola transacción).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class AgendaConcurrenciaIntegrationTest extends BaseIntegracionConDatos {

    private static final int RONDAS = 6;
    private static final LocalDate FECHA = LocalDate.now().plusDays(2);

    private LocalDateTime slot(int indice) {
        return LocalDateTime.of(FECHA, LocalTime.of(8, 0).plusMinutes(30L * indice));
    }

    /** Pasa a ATENDIDO los turnos pendientes del médico, para que no cuenten contra el tope de turnos por paciente. */
    private void cerrarPendientesDe(Medico medico) {
        jdbc.update("UPDATE turno SET estado = 'ATENDIDO' WHERE medico_id = ? AND estado IN ('RESERVADO', 'CONFIRMADO')", medico.getId());
    }

    // --- la reserva simultánea ---

    @Test
    void dos_pacientes_que_reservan_a_la_vez_el_mismo_turno_de_un_medico_uno_gana_y_el_otro_recibe_409() throws Exception {
        Medico medico = medico();
        horario(medico, consultorio(), FECHA);
        Usuario pacienteA = paciente().getUsuario();
        Usuario pacienteB = paciente().getUsuario();

        for (int i = 0; i < RONDAS; i++) {
            int[] resultado = enParalelo(reservar(pacienteA, medico, slot(i)), reservar(pacienteB, medico, slot(i)));

            assertThat(ordenados(resultado)).as("ronda %d", i).containsExactly(201, 409);
            cerrarPendientesDe(medico);   // si no, tras 2 rondas el tope de 2 turnos pendientes frenaría a los pacientes
        }
        long activos = turnoRepository.findByMedicoIdAndInicioBetween(medico.getId(), FECHA.atStartOfDay(), FECHA.plusDays(1).atStartOfDay())
                .stream().filter(t -> t.getEstado() != EstadoTurno.CANCELADO).count();
        assertThat(activos).isEqualTo(RONDAS);
    }

    @Test
    void el_mismo_paciente_no_puede_quedar_en_dos_consultorios_a_la_misma_hora_aunque_lo_pida_a_la_vez() throws Exception {
        Medico medicoA = medico();
        Medico medicoB = medico();
        horario(medicoA, consultorio(), FECHA);
        horario(medicoB, consultorio(), FECHA);
        Paciente paciente = paciente();

        for (int i = 0; i < RONDAS; i++) {
            int[] resultado = enParalelo(reservar(paciente.getUsuario(), medicoA, slot(i)), reservar(paciente.getUsuario(), medicoB, slot(i)));

            assertThat(ordenados(resultado)).as("ronda %d", i).containsExactly(201, 409);
            cerrarPendientesDe(medicoA);
            cerrarPendientesDe(medicoB);
        }
        long activos = turnoRepository.findByPacienteIdOrderByInicioDesc(paciente.getId()).stream()
                .filter(t -> t.getEstado() != EstadoTurno.CANCELADO).count();
        assertThat(activos).isEqualTo(RONDAS);
    }

    // --- la carrera entre dos acciones sobre el mismo turno ---

    @Test
    void cancelar_y_marcar_ausente_a_la_vez_no_pisa_en_silencio_a_la_otra_accion() throws Exception {
        Medico medico = medico();
        Consultorio consultorio = consultorio();
        Paciente paciente = paciente();
        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());

        for (int i = 0; i < RONDAS * 2; i++) {
            LocalDateTime inicio = slot(i).plusDays(10);
            Turno turno = turnoRepository.saveAndFlush(Turno.builder().medico(medico).paciente(paciente).consultorio(consultorio)
                    .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(EstadoTurno.CONFIRMADO).build());

            int[] r = enParalelo(
                    patch("/api/turnos/" + turno.getId() + "/cancelar").header("Authorization", token(paciente.getUsuario()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivoCancelacionId\":" + motivo.getId() + "}"),
                    patch("/api/turnos/" + turno.getId() + "/marcar-ausente").header("Authorization", token(medico.getUsuario())));
            int cancelar = r[0];
            int ausente = r[1];

            // Exactamente una de las dos acciones se aplicó; la otra recibió un error (409 por la
            // modificación simultánea, o 400 si ya encontró el turno en el estado nuevo).
            assertThat(cancelar == 200 ^ ausente == 200).as("ronda %d: cancelar=%d ausente=%d", i, cancelar, ausente).isTrue();
            assertThat(cancelar == 200 ? ausente : cancelar).isIn(400, 409);

            // Y el estado final es el de la acción que dijo 200: no se perdió ninguna en silencio.
            Map<String, Object> fila = jdbc.queryForMap("SELECT estado, motivo_cancelacion_id FROM turno WHERE id = ?", turno.getId());
            if (cancelar == 200) {
                assertThat(fila.get("estado")).isEqualTo("CANCELADO");
                assertThat(fila.get("motivo_cancelacion_id")).isNotNull();
            } else {
                assertThat(fila.get("estado")).isEqualTo("AUSENTE");
                assertThat(fila.get("motivo_cancelacion_id")).isNull();
            }
        }
    }

    // --- la base misma rechaza los solapamientos ---

    @Test
    void la_base_rechaza_dos_turnos_activos_del_mismo_medico_que_se_solapan_aunque_no_empiecen_igual() {
        Medico medico = medico();
        Consultorio consultorio = consultorio();
        Paciente a = paciente();
        Paciente b = paciente();
        LocalDateTime inicio = slot(0);
        turnoRepository.saveAndFlush(turno(medico, a, consultorio, inicio, inicio.plusMinutes(30), EstadoTurno.RESERVADO));

        assertThatThrownBy(() -> turnoRepository.saveAndFlush(
                turno(medico, b, consultorio, inicio.plusMinutes(10), inicio.plusMinutes(40), EstadoTurno.RESERVADO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_turno_medico_sin_solapes");
    }

    @Test
    void la_base_rechaza_dos_turnos_activos_del_mismo_paciente_que_se_solapan() {
        Medico medicoA = medico();
        Medico medicoB = medico();
        Consultorio consultorio = consultorio();
        Paciente paciente = paciente();
        LocalDateTime inicio = slot(0);
        turnoRepository.saveAndFlush(turno(medicoA, paciente, consultorio, inicio, inicio.plusMinutes(30), EstadoTurno.RESERVADO));

        assertThatThrownBy(() -> turnoRepository.saveAndFlush(
                turno(medicoB, paciente, consultorio, inicio.plusMinutes(15), inicio.plusMinutes(45), EstadoTurno.CONFIRMADO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_turno_paciente_sin_solapes");
    }

    @Test
    void turnos_consecutivos_o_cancelados_no_cuentan_como_solapados() {
        Medico medico = medico();
        Consultorio consultorio = consultorio();
        Paciente a = paciente();
        Paciente b = paciente();
        Paciente c = paciente();
        LocalDateTime inicio = slot(0);
        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());

        turnoRepository.saveAndFlush(turno(medico, a, consultorio, inicio, inicio.plusMinutes(30), EstadoTurno.RESERVADO));
        // justo a continuación: 09:00-09:30 y 09:30-10:00 no se pisan
        turnoRepository.saveAndFlush(turno(medico, b, consultorio, inicio.plusMinutes(30), inicio.plusMinutes(60), EstadoTurno.RESERVADO));
        // uno cancelado en el mismo horario que otro activo tampoco bloquea
        Turno cancelado = turno(medico, c, consultorio, inicio, inicio.plusMinutes(30), EstadoTurno.CANCELADO);
        cancelado.setMotivoCancelacion(motivo);
        turnoRepository.saveAndFlush(cancelado);

        assertThat(turnoRepository.findByMedicoIdAndInicioBetween(medico.getId(), FECHA.atStartOfDay(), FECHA.plusDays(1).atStartOfDay())).hasSize(3);
    }

    // --- cambiar o borrar un horario con turnos reservados (por HTTP) ---

    @Test
    void no_se_puede_cambiar_la_duracion_ni_borrar_un_horario_con_un_turno_reservado_y_si_cuando_se_cancela() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        Medico medico = medico();
        Consultorio consultorio = consultorio();
        HorarioAtencion horario = horario(medico, consultorio, FECHA);
        Paciente paciente = paciente();
        MotivoCancelacion motivo = motivoRepository.saveAndFlush(MotivoCancelacion.builder().nombre(PREFIJO + UUID.randomUUID()).build());

        assertThat(estado(reservar(paciente.getUsuario(), medico, slot(3)))).isEqualTo(201);
        String nuevaDuracion = "{\"medicoId\":" + medico.getId() + ",\"consultorioId\":" + consultorio.getId()
                + ",\"diaSemana\":\"" + FECHA.getDayOfWeek() + "\",\"horaInicio\":\"08:00:00\",\"horaFin\":\"20:00:00\""
                + ",\"duracionTurnoMin\":20,\"vigenteDesde\":\"" + LocalDate.now().minusDays(30) + "\"}";

        mockMvc.perform(put("/api/horarios-atencion/" + horario.getId()).header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(nuevaDuracion))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("turno")));
        mockMvc.perform(delete("/api/horarios-atencion/" + horario.getId()).header("Authorization", token(admin)))
                .andExpect(status().isConflict());

        // Con el turno cancelado ya no depende de nada y el horario se puede borrar.
        Long turnoId = turnoRepository.findByPacienteIdOrderByInicioDesc(paciente.getId()).get(0).getId();
        mockMvc.perform(patch("/api/turnos/" + turnoId + "/cancelar").header("Authorization", token(paciente.getUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivoCancelacionId\":" + motivo.getId() + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/horarios-atencion/" + horario.getId()).header("Authorization", token(admin)))
                .andExpect(status().isNoContent());
    }

    private Turno turno(Medico medico, Paciente paciente, Consultorio consultorio, LocalDateTime inicio, LocalDateTime fin, EstadoTurno estado) {
        return Turno.builder().medico(medico).paciente(paciente).consultorio(consultorio).creadoPor(paciente.getUsuario())
                .inicio(inicio).fin(fin).estado(estado).build();
    }
}
