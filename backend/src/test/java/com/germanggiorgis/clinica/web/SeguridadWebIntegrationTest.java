package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import com.germanggiorgis.clinica.security.CustomUserDetailsService;
import com.germanggiorgis.clinica.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la capa HTTP completa (filtro JWT + SecurityConfig + controllers +
 * GlobalExceptionHandler) contra Postgres real ("clinica_test"). Complementa
 * a los tests unitarios de servicios, que no ven nada de esto. Cada test
 * corre en una transacción que se revierte, así no deja datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class SeguridadWebIntegrationTest {

    private static final String SECRETO_TEST = "secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef";

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
    private HorarioAtencionRepository horarioAtencionRepository;
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

    private Paciente crearPaciente() {
        Usuario usuario = crearUsuario(Rol.PACIENTE);
        return pacienteRepository.saveAndFlush(Paciente.builder().usuario(usuario)
                .dni(UUID.randomUUID().toString().substring(0, 12)).fechaNacimiento(LocalDate.of(1990, 1, 1)).build());
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwtService.generateToken(userDetailsService.loadUserByUsername(usuario.getEmail()));
    }

    // --- catálogos: lectura pública, escritura solo ADMIN ---

    @ParameterizedTest
    @ValueSource(strings = {"/api/especialidades", "/api/medicos", "/api/patologias", "/api/medicamentos",
            "/api/obras-sociales", "/api/estudios", "/api/consultorios", "/api/motivos-cancelacion"})
    void la_lectura_de_catalogos_es_publica(String ruta) throws Exception {
        mockMvc.perform(get(ruta)).andExpect(status().isOk());
    }

    @Test
    void escribir_un_catalogo_sin_token_da_401_con_el_json_de_la_api() throws Exception {
        mockMvc.perform(post("/api/especialidades").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void escribir_un_catalogo_como_paciente_da_403_con_el_json_de_la_api() throws Exception {
        Usuario paciente = crearPaciente().getUsuario();
        mockMvc.perform(post("/api/especialidades").header("Authorization", token(paciente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void escribir_un_catalogo_como_medico_da_403() throws Exception {
        Usuario medico = crearUsuario(Rol.MEDICO);
        mockMvc.perform(post("/api/medicamentos").header("Authorization", token(medico))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void escribir_un_catalogo_como_admin_funciona() throws Exception {
        Usuario admin = crearUsuario(Rol.ADMIN);
        mockMvc.perform(post("/api/especialidades").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Especialidad de prueba " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void un_dato_invalido_da_400_con_el_detalle_por_campo() throws Exception {
        Usuario admin = crearUsuario(Rol.ADMIN);
        mockMvc.perform(post("/api/especialidades").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    // --- endpoints que exigen login / rol ---

    @ParameterizedTest
    @ValueSource(strings = {"/api/turnos/mios", "/api/turnos/agenda", "/api/historial/mio",
            "/api/afiliaciones/mias", "/api/usuarios"})
    void los_endpoints_privados_sin_token_dan_401(String ruta) throws Exception {
        mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_usuarios_es_solo_para_admin() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", token(crearPaciente().getUsuario())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios").header("Authorization", token(crearUsuario(Rol.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void un_paciente_no_puede_ver_la_agenda_de_un_medico() throws Exception {
        mockMvc.perform(get("/api/turnos/agenda").header("Authorization", token(crearPaciente().getUsuario())))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_paciente_ve_sus_turnos_vacios_al_principio() throws Exception {
        mockMvc.perform(get("/api/turnos/mios").header("Authorization", token(crearPaciente().getUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- el filtro JWT ante tokens malos ---

    @Test
    void un_token_basura_da_401() throws Exception {
        mockMvc.perform(get("/api/turnos/mios").header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_token_firmado_con_otro_secreto_da_401() throws Exception {
        Usuario paciente = crearPaciente().getUsuario();
        JwtService otroServicio = new JwtService("otro-secreto-completamente-distinto-0123456789abcdef-xyz", 3_600_000);
        String tokenAjeno = otroServicio.generateToken(userDetailsService.loadUserByUsername(paciente.getEmail()));

        mockMvc.perform(get("/api/turnos/mios").header("Authorization", "Bearer " + tokenAjeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_token_vencido_da_401() throws Exception {
        Usuario paciente = crearPaciente().getUsuario();
        JwtService servicioConExpiracionNegativa = new JwtService(SECRETO_TEST, -60_000);
        String tokenVencido = servicioConExpiracionNegativa.generateToken(userDetailsService.loadUserByUsername(paciente.getEmail()));

        mockMvc.perform(get("/api/turnos/mios").header("Authorization", "Bearer " + tokenVencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_token_valido_de_un_usuario_desactivado_da_401_y_no_500() throws Exception {
        Usuario paciente = crearPaciente().getUsuario();
        String tokenValido = token(paciente);
        paciente.setActivo(false);
        usuarioRepository.saveAndFlush(paciente);

        mockMvc.perform(get("/api/turnos/mios").header("Authorization", tokenValido))
                .andExpect(status().isUnauthorized());
    }

    // --- login y registro ---

    @Test
    void el_login_con_credenciales_correctas_devuelve_un_token() throws Exception {
        Usuario paciente = crearPaciente().getUsuario();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + paciente.getEmail() + "\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("PACIENTE"));
    }

    @Test
    void el_login_con_contrasena_incorrecta_da_401() throws Exception {
        Usuario paciente = crearPaciente().getUsuario();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + paciente.getEmail() + "\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void el_registro_publico_crea_un_paciente_y_devuelve_token() throws Exception {
        String email = "nuevo-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        String dni = UUID.randomUUID().toString().substring(0, 12);
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Diaz\",\"email\":\"" + email + "\",\"password\":\"clave-segura-1234\","
                                + "\"dni\":\"" + dni + "\",\"fechaNacimiento\":\"1990-05-20\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("PACIENTE"));
    }

    @Test
    void el_registro_con_un_email_repetido_da_409() throws Exception {
        Usuario existente = crearPaciente().getUsuario();
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Diaz\",\"email\":\"" + existente.getEmail() + "\",\"password\":\"clave-segura-1234\","
                                + "\"dni\":\"" + UUID.randomUUID().toString().substring(0, 12) + "\",\"fechaNacimiento\":\"1990-05-20\"}"))
                .andExpect(status().isConflict());
    }

    // --- el flujo completo de reserva, con la restricción real de la base ---

    @Test
    void dos_pacientes_no_pueden_reservar_el_mismo_horario_del_mismo_medico() throws Exception {
        Usuario usuarioMedico = crearUsuario(Rol.MEDICO);
        Medico medico = medicoRepository.saveAndFlush(Medico.builder().usuario(usuarioMedico)
                .matricula(UUID.randomUUID().toString().substring(0, 12)).build());
        Consultorio consultorio = consultorioRepository.saveAndFlush(
                Consultorio.builder().nombre("Consultorio " + UUID.randomUUID()).build());
        LocalDate fecha = LocalDate.now().plusDays(3);
        horarioAtencionRepository.saveAndFlush(HorarioAtencion.builder().medico(medico).consultorio(consultorio)
                .diaSemana(fecha.getDayOfWeek()).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(12, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.now().minusDays(30)).build());

        String pedido = "{\"medicoId\":" + medico.getId() + ",\"inicio\":\"" + fecha + "T09:00:00\"}";
        String tokenA = token(crearPaciente().getUsuario());
        String tokenB = token(crearPaciente().getUsuario());

        mockMvc.perform(post("/api/turnos").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(pedido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESERVADO"));

        // El segundo paciente choca contra el índice único parcial de la base.
        mockMvc.perform(post("/api/turnos").header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content(pedido))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void la_disponibilidad_es_publica_y_refleja_el_horario_del_medico() throws Exception {
        Usuario usuarioMedico = crearUsuario(Rol.MEDICO);
        Medico medico = medicoRepository.saveAndFlush(Medico.builder().usuario(usuarioMedico)
                .matricula(UUID.randomUUID().toString().substring(0, 12)).build());
        Consultorio consultorio = consultorioRepository.saveAndFlush(
                Consultorio.builder().nombre("Consultorio " + UUID.randomUUID()).build());
        LocalDate fecha = LocalDate.now().plusDays(3);
        horarioAtencionRepository.saveAndFlush(HorarioAtencion.builder().medico(medico).consultorio(consultorio)
                .diaSemana(fecha.getDayOfWeek()).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(10, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.now().minusDays(30)).build());

        mockMvc.perform(get("/api/medicos/" + medico.getId() + "/disponibilidad").param("fecha", fecha.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horariosLibres.length()").value(2));
    }

    @Test
    void un_medico_inexistente_da_404_con_el_formato_de_error_de_la_api() throws Exception {
        mockMvc.perform(get("/api/medicos/999999/disponibilidad").param("fecha", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
