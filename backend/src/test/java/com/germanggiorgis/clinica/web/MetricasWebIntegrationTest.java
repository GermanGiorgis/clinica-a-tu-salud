package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.Encuesta;
import com.germanggiorgis.clinica.model.EstadoTurno;
import com.germanggiorgis.clinica.model.Factura;
import com.germanggiorgis.clinica.model.MedioPago;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.MotivoCancelacion;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Turno;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.EncuestaRepository;
import com.germanggiorgis.clinica.repository.FacturaRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import com.germanggiorgis.clinica.security.CustomUserDetailsService;
import com.germanggiorgis.clinica.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los unit tests de MetricasService mockean los repositorios, así que no
 * prueban que las consultas JPQL de agregación sean válidas ni que sumen bien
 * contra la base. Este test sí: crea datos reales y mira el JSON del endpoint
 * (filtrando por el médico recién creado, para no depender de lo que haya en
 * la base de test). Todo se revierte al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class MetricasWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private ConsultorioRepository consultorioRepository;
    @Autowired
    private TurnoRepository turnoRepository;
    @Autowired
    private MotivoCancelacionRepository motivoCancelacionRepository;
    @Autowired
    private EncuestaRepository encuestaRepository;
    @Autowired
    private FacturaRepository facturaRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private CustomUserDetailsService userDetailsService;

    private Usuario crearUsuario(Rol rol) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .email(rol.name().toLowerCase() + "-" + sufijo + "@test.local")
                .passwordHash(passwordEncoder.encode("clave1234"))
                .rol(rol).nombre("Nombre").apellido("Apellido").build());
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwtService.generateToken(userDetailsService.loadUserByUsername(usuario.getEmail()));
    }

    private Paciente crearPaciente() {
        return pacienteRepository.saveAndFlush(Paciente.builder().usuario(crearUsuario(Rol.PACIENTE))
                .dni(UUID.randomUUID().toString().substring(0, 12)).fechaNacimiento(LocalDate.of(1990, 1, 1)).build());
    }

    private Turno crearTurno(Medico medico, Paciente paciente, Consultorio consultorio, EstadoTurno estado, int dias) {
        LocalDateTime inicio = LocalDateTime.now().minusDays(dias).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Turno turno = Turno.builder().medico(medico).paciente(paciente).consultorio(consultorio)
                .creadoPor(paciente.getUsuario()).inicio(inicio).fin(inicio.plusMinutes(30)).estado(estado).build();
        if (estado == EstadoTurno.CANCELADO) {
            // La base exige un motivo en los turnos cancelados (ck_turno_motivo_cancelacion).
            turno.setMotivoCancelacion(motivoCancelacionRepository.saveAndFlush(
                    MotivoCancelacion.builder().nombre("Motivo " + UUID.randomUUID()).build()));
        }
        return turnoRepository.saveAndFlush(turno);
    }

    @Test
    void sin_token_da_401() throws Exception {
        mockMvc.perform(get("/api/metricas")).andExpect(status().isUnauthorized());
    }

    @Test
    void un_paciente_no_puede_ver_las_metricas() throws Exception {
        mockMvc.perform(get("/api/metricas").header("Authorization", token(crearPaciente().getUsuario())))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_medico_no_puede_ver_las_metricas() throws Exception {
        mockMvc.perform(get("/api/metricas").header("Authorization", token(crearUsuario(Rol.MEDICO))))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_admin_ve_la_estructura_completa_aunque_no_haya_datos() throws Exception {
        mockMvc.perform(get("/api/metricas").header("Authorization", token(crearUsuario(Rol.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTurnos").isNumber())
                .andExpect(jsonPath("$.turnosPorEstado", hasSize(EstadoTurno.values().length)))
                .andExpect(jsonPath("$.facturacionPorMedioPago", hasSize(MedioPago.values().length)))
                .andExpect(jsonPath("$.medicos").isArray());
    }

    @Test
    void las_consultas_de_agregacion_suman_bien_contra_la_base_real() throws Exception {
        Usuario admin = crearUsuario(Rol.ADMIN);
        Medico medico = medicoRepository.saveAndFlush(Medico.builder().usuario(crearUsuario(Rol.MEDICO))
                .matricula(UUID.randomUUID().toString().substring(0, 12)).build());
        Consultorio consultorio = consultorioRepository.saveAndFlush(
                Consultorio.builder().nombre("Consultorio " + UUID.randomUUID()).build());
        Paciente pacienteA = crearPaciente();
        Paciente pacienteB = crearPaciente();

        Turno atendido1 = crearTurno(medico, pacienteA, consultorio, EstadoTurno.ATENDIDO, 1);
        Turno atendido2 = crearTurno(medico, pacienteB, consultorio, EstadoTurno.ATENDIDO, 2);
        crearTurno(medico, pacienteA, consultorio, EstadoTurno.AUSENTE, 3);
        crearTurno(medico, pacienteA, consultorio, EstadoTurno.CANCELADO, 4);

        encuestaRepository.saveAndFlush(Encuesta.builder().turno(atendido1).puntaje(5).build());
        encuestaRepository.saveAndFlush(Encuesta.builder().turno(atendido2).puntaje(4).build());
        facturaRepository.saveAndFlush(Factura.builder().turno(atendido1).total(new BigDecimal("1000.50"))
                .medioPago(MedioPago.EFECTIVO).build());
        facturaRepository.saveAndFlush(Factura.builder().turno(atendido2).total(new BigDecimal("2000.25"))
                .medioPago(MedioPago.EFECTIVO).build());

        String filaDelMedico = "$.medicos[?(@.medicoId==" + medico.getId() + ")]";
        mockMvc.perform(get("/api/metricas").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filaDelMedico + ".turnosAtendidos").value(2))
                .andExpect(jsonPath(filaDelMedico + ".turnosAusentes").value(1))
                .andExpect(jsonPath(filaDelMedico + ".encuestas").value(2))
                .andExpect(jsonPath(filaDelMedico + ".puntajePromedio").value(4.5))
                .andExpect(jsonPath(filaDelMedico + ".facturado").value(3000.75))
                // Los totales globales incluyen lo que ya hubiera en la base, así que solo se pide un mínimo.
                .andExpect(jsonPath("$.totalTurnos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.pacientesAtendidos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.facturacionTotal").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3000.75)))
                .andExpect(jsonPath("$.totalEncuestas").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }
}
