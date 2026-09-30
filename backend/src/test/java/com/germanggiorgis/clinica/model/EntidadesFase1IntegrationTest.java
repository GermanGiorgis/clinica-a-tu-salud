package com.germanggiorgis.clinica.model;

import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba las entidades de la fase 1 contra un Postgres real (base
 * "clinica_test"), no contra H2: la regla anti doble-reserva depende de un
 * índice único parcial que H2 no soporta (ver docs/modelo-datos.md).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test")
class EntidadesFase1IntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private EspecialidadRepository especialidadRepository;
    @Autowired
    private ConsultorioRepository consultorioRepository;
    @Autowired
    private HorarioAtencionRepository horarioAtencionRepository;
    @Autowired
    private TurnoRepository turnoRepository;

    @Test
    void guarda_y_relaciona_una_agenda_completa() {
        Usuario usuarioMedico = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("dra.perez@clinica.test")
                .passwordHash("hash-no-real")
                .rol(Rol.MEDICO)
                .nombre("Ana")
                .apellido("Pérez")
                .build());

        Especialidad cardiologia = especialidadRepository.saveAndFlush(
                Especialidad.builder().nombre("Cardiología").build());

        Medico medico = medicoRepository.saveAndFlush(Medico.builder()
                .usuario(usuarioMedico)
                .matricula("MP-1234")
                .especialidades(java.util.List.of(cardiologia))
                .build());

        Consultorio consultorio = consultorioRepository.saveAndFlush(
                Consultorio.builder().nombre("Consultorio 1").piso(2).build());

        HorarioAtencion horario = horarioAtencionRepository.saveAndFlush(HorarioAtencion.builder()
                .medico(medico)
                .consultorio(consultorio)
                .diaSemana(DayOfWeek.MONDAY)
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(13, 0))
                .duracionTurnoMin(30)
                .vigenteDesde(LocalDate.of(2026, 1, 1))
                .build());

        Usuario usuarioPaciente = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("juan.gomez@clinica.test")
                .passwordHash("hash-no-real")
                .rol(Rol.PACIENTE)
                .nombre("Juan")
                .apellido("Gómez")
                .build());

        Paciente paciente = pacienteRepository.saveAndFlush(Paciente.builder()
                .usuario(usuarioPaciente)
                .dni("30111222")
                .fechaNacimiento(LocalDate.of(1990, 5, 20))
                .build());

        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 9, 0);
        Turno turno = turnoRepository.saveAndFlush(Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .consultorio(consultorio)
                .creadoPor(usuarioPaciente)
                .inicio(inicio)
                .fin(inicio.plusMinutes(30))
                .build());

        em.clear(); // fuerza a releer de la base, no del cache de primer nivel

        Turno recargado = turnoRepository.findById(turno.getId()).orElseThrow();
        assertThat(recargado.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(recargado.getMedico().getMatricula()).isEqualTo("MP-1234");
        assertThat(recargado.getMedico().getEspecialidades()).extracting(Especialidad::getNombre)
                .containsExactly("Cardiología");
        assertThat(recargado.getPaciente().getDni()).isEqualTo("30111222");
        assertThat(recargado.getCreadoPor().getEmail()).isEqualTo("juan.gomez@clinica.test");

        assertThat(horarioAtencionRepository.findByMedicoIdAndDiaSemana(medico.getId(), DayOfWeek.MONDAY))
                .hasSize(1);
        assertThat(horario.getDiaSemana()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void no_permite_dos_turnos_activos_del_mismo_medico_a_la_misma_hora() {
        Usuario usuarioMedico = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("dr.ruiz@clinica.test")
                .passwordHash("hash-no-real")
                .rol(Rol.MEDICO)
                .nombre("Carlos")
                .apellido("Ruiz")
                .build());
        Medico medico = medicoRepository.saveAndFlush(
                Medico.builder().usuario(usuarioMedico).matricula("MP-5678").build());
        Consultorio consultorio = consultorioRepository.saveAndFlush(
                Consultorio.builder().nombre("Consultorio 2").build());

        Usuario usuarioPacienteA = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("paciente.a@clinica.test").passwordHash("x").rol(Rol.PACIENTE)
                .nombre("A").apellido("A").build());
        Paciente pacienteA = pacienteRepository.saveAndFlush(Paciente.builder()
                .usuario(usuarioPacienteA).dni("11111111").fechaNacimiento(LocalDate.of(1980, 1, 1)).build());

        Usuario usuarioPacienteB = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("paciente.b@clinica.test").passwordHash("x").rol(Rol.PACIENTE)
                .nombre("B").apellido("B").build());
        Paciente pacienteB = pacienteRepository.saveAndFlush(Paciente.builder()
                .usuario(usuarioPacienteB).dni("22222222").fechaNacimiento(LocalDate.of(1980, 1, 1)).build());

        LocalDateTime mismaHora = LocalDateTime.of(2026, 11, 2, 10, 0);

        turnoRepository.saveAndFlush(Turno.builder()
                .paciente(pacienteA).medico(medico).consultorio(consultorio).creadoPor(usuarioPacienteA)
                .inicio(mismaHora).fin(mismaHora.plusMinutes(30))
                .build());

        Turno turnoSuperpuesto = Turno.builder()
                .paciente(pacienteB).medico(medico).consultorio(consultorio).creadoPor(usuarioPacienteB)
                .inicio(mismaHora).fin(mismaHora.plusMinutes(30))
                .build();

        // El índice único parcial (medico_id, inicio) WHERE estado <> 'CANCELADO'
        // es lo que realmente impide la doble reserva, a nivel de base de datos.
        assertThatThrownBy(() -> turnoRepository.saveAndFlush(turnoSuperpuesto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
