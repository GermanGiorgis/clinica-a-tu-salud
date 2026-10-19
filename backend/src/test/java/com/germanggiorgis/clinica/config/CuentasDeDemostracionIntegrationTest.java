package com.germanggiorgis.clinica.config;

import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las cuentas de demostración (una por rol) con las que cualquiera puede probar la aplicación. Cada prueba corre en una
 * transacción que se deshace: en la base de pruebas el cargador está apagado, como en producción.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class CuentasDeDemostracionIntegrationTest {

    @Autowired private CuentasDeDemostracion cargador;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PacienteRepository pacienteRepository;
    @Autowired private MedicoRepository medicoRepository;
    @Autowired private HorarioAtencionRepository horarioRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private void cargar() {
        ReflectionTestUtils.setField(cargador, "activado", true);
        cargador.run(null);
    }

    private Usuario cuenta(String email) {
        return usuarioRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    @Test
    void apagado_no_crea_ninguna_cuenta() {
        ReflectionTestUtils.setField(cargador, "activado", false);

        cargador.run(null);

        assertThat(usuarioRepository.existsByEmailIgnoreCase(CuentasDeDemostracion.EMAIL_PACIENTE)).isFalse();
    }

    @Test
    void crea_una_cuenta_por_rol_con_la_contrasena_publica() {
        cargar();

        assertThat(cuenta(CuentasDeDemostracion.EMAIL_PACIENTE).getRol()).isEqualTo(Rol.PACIENTE);
        assertThat(cuenta(CuentasDeDemostracion.EMAIL_MEDICO).getRol()).isEqualTo(Rol.MEDICO);
        assertThat(cuenta(CuentasDeDemostracion.EMAIL_RECEPCION).getRol()).isEqualTo(Rol.RECEPCIONISTA);
        for (String email : List.of(CuentasDeDemostracion.EMAIL_PACIENTE, CuentasDeDemostracion.EMAIL_MEDICO, CuentasDeDemostracion.EMAIL_RECEPCION)) {
            Usuario usuario = cuenta(email);
            assertThat(usuario.isActivo()).isTrue();
            assertThat(passwordEncoder.matches(CuentasDeDemostracion.PASSWORD, usuario.getPasswordHash())).as(email).isTrue();
        }
    }

    @Test
    void el_paciente_demo_ya_tiene_la_identidad_verificada_para_poder_reservar() {
        cargar();

        Paciente paciente = pacienteRepository.findAll().stream()
                .filter(p -> p.getUsuario().getEmail().equals(CuentasDeDemostracion.EMAIL_PACIENTE)).findFirst().orElseThrow();

        assertThat(paciente.isIdentidadVerificada()).isTrue();
        assertThat(paciente.getDni()).matches("\\d{6,9}");
    }

    @Test
    void el_medico_demo_atiende_de_lunes_a_viernes_y_tiene_especialidades() {
        cargar();

        Medico medico = medicoRepository.findByUsuarioId(cuenta(CuentasDeDemostracion.EMAIL_MEDICO).getId()).orElseThrow();

        assertThat(medico.getEspecialidades()).isNotEmpty();
        assertThat(horarioRepository.findByMedicoId(medico.getId())).extracting(h -> h.getDiaSemana())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void deja_un_turno_de_ejemplo_futuro_en_un_dia_y_hora_en_que_el_medico_atiende() {
        cargar();

        Turno turno = turnoRepository.findAll().stream()
                .filter(t -> t.getPaciente().getUsuario().getEmail().equals(CuentasDeDemostracion.EMAIL_PACIENTE)).findFirst().orElseThrow();

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(turno.getInicio()).isAfter(LocalDateTime.now());
        assertThat(turno.getInicio().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(horarioRepository.findByMedicoIdAndDiaSemana(turno.getMedico().getId(), turno.getInicio().getDayOfWeek()))
                .anySatisfy(h -> assertThat(turno.getInicio().toLocalTime()).isBetween(h.getHoraInicio(), h.getHoraFin()));
    }

    @Test
    void cargar_dos_veces_no_repite_cuentas_ni_turnos() {
        cargar();
        long usuarios = usuarioRepository.count();
        long turnos = turnoRepository.count();

        cargar();

        assertThat(usuarioRepository.count()).isEqualTo(usuarios);
        assertThat(turnoRepository.count()).isEqualTo(turnos);
    }
}
