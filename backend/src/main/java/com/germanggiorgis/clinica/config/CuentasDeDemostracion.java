package com.germanggiorgis.clinica.config;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una cuenta de demostración por rol, con contraseña pública, para que quien clone el repositorio (o abra la
 * demo en línea) pueda probar cada pantalla sin tener que crear usuarios. Se activa con
 * {@code SEED_DEMO_CUENTAS=true}; es solo para entornos descartables (tu máquina, docker compose, la demo
 * pública), nunca para uno con datos reales. El administrador de demostración lo crea {@link com.germanggiorgis.clinica.security.AdminSeeder}.
 *
 * <p>Es idempotente: lo que ya existe no se repite, así que se puede dejar activado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CuentasDeDemostracion implements ApplicationRunner {

    public static final String PASSWORD = "demo-clinica-2026";
    public static final String EMAIL_PACIENTE = "paciente@clinica-demo.test";
    public static final String EMAIL_MEDICO = "medico@clinica-demo.test";
    public static final String EMAIL_RECEPCION = "recepcion@clinica-demo.test";
    static final String DNI_PACIENTE = "40000001";

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final ConsultorioRepository consultorioRepository;
    private final HorarioAtencionRepository horarioRepository;
    private final TurnoRepository turnoRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${clinica.seed.demo-cuentas:false}")
    private boolean activado;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!activado) {
            return;
        }
        String hash = passwordEncoder.encode(PASSWORD);
        Medico medico = medicoDeDemostracion(hash);
        Paciente paciente = pacienteDeDemostracion(hash);
        usuarioSiNoExiste(EMAIL_RECEPCION, "Rita", "Demo", Rol.RECEPCIONISTA, hash);
        turnoDeEjemplo(paciente, medico);
        log.warn("Cuentas de DEMOSTRACIÓN disponibles (contraseña pública: {}). Solo para entornos descartables.", PASSWORD);
    }

    private Usuario usuarioSiNoExiste(String email, String nombre, String apellido, Rol rol, String hash) {
        return usuarioRepository.findByEmailIgnoreCase(email).orElseGet(() -> usuarioRepository.save(Usuario.builder()
                .email(email).passwordHash(hash).rol(rol).nombre(nombre).apellido(apellido).build()));
    }

    private Paciente pacienteDeDemostracion(String hash) {
        Usuario usuario = usuarioSiNoExiste(EMAIL_PACIENTE, "Paula", "Demo", Rol.PACIENTE, hash);
        return pacienteRepository.findAll().stream().filter(p -> p.getUsuario().getId().equals(usuario.getId())).findFirst()
                .orElseGet(() -> pacienteRepository.save(Paciente.builder().usuario(usuario).dni(DNI_PACIENTE)
                        .fechaNacimiento(LocalDate.of(1990, 5, 20)).identidadVerificada(true).build()));
    }

    /** El médico atiende de lunes a viernes por la mañana, así que siempre tiene horarios libres a mano. */
    private Medico medicoDeDemostracion(String hash) {
        Usuario usuario = usuarioSiNoExiste(EMAIL_MEDICO, "Martín", "Demo", Rol.MEDICO, hash);
        return medicoRepository.findByUsuarioId(usuario.getId()).orElseGet(() -> {
            List<Especialidad> especialidades = new ArrayList<>();
            for (String nombre : List.of("Clínica Médica", "Medicina Familiar")) {
                especialidadRepository.findAll().stream().filter(e -> e.getNombre().equals(nombre)).findFirst().ifPresent(especialidades::add);
            }
            Medico medico = medicoRepository.save(Medico.builder().usuario(usuario).matricula("DEMO-LOGIN")
                    .especialidades(especialidades).build());
            Consultorio consultorio = consultorioRepository.save(Consultorio.builder()
                    .nombre("Consultorio Demo").ubicacion("Planta baja").piso(0).build());
            for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
                horarioRepository.save(HorarioAtencion.builder().medico(medico).consultorio(consultorio).diaSemana(dia)
                        .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(13, 0)).duracionTurnoMin(30)
                        .vigenteDesde(LocalDate.now(clock).minusDays(30)).build());
            }
            return medico;
        });
    }

    /** Un turno ya reservado para el paciente demo, para que "Mis turnos" y la agenda del médico no estén vacíos. */
    private void turnoDeEjemplo(Paciente paciente, Medico medico) {
        boolean yaTiene = turnoRepository.findAll().stream().anyMatch(t -> t.getPaciente().getId().equals(paciente.getId())
                && t.getEstado() == EstadoTurno.RESERVADO && t.getInicio().isAfter(LocalDateTime.now(clock)));
        if (yaTiene) {
            return;
        }
        LocalDate dia = LocalDate.now(clock).plusDays(2);
        while (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        HorarioAtencion horario = horarioRepository.findByMedicoIdAndDiaSemana(medico.getId(), dia.getDayOfWeek()).stream().findFirst().orElse(null);
        if (horario == null) {
            return;
        }
        LocalDateTime inicio = dia.atTime(10, 0);
        turnoRepository.save(Turno.builder().paciente(paciente).medico(medico).consultorio(horario.getConsultorio())
                .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30))
                .motivoConsulta("Control general (turno de ejemplo)").build());
    }
}
