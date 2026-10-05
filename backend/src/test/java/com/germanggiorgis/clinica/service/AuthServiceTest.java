package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.dto.AuthResponse;
import com.germanggiorgis.clinica.dto.LoginRequest;
import com.germanggiorgis.clinica.dto.RegistroPacienteRequest;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import com.germanggiorgis.clinica.security.CustomUserDetailsService;
import com.germanggiorgis.clinica.security.DemasiadosIntentosException;
import com.germanggiorgis.clinica.security.JwtService;
import com.germanggiorgis.clinica.security.LoginAttemptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IP = "203.0.113.7";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    private RegistroPacienteRequest registroValido() {
        return new RegistroPacienteRequest("Juan", "Gómez", "juan@clinica.test", "paciente-seguro-123",
                null, "30111222", LocalDate.of(1990, 5, 20));
    }

    private void prepararAltaExitosa() {
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(false);
        when(passwordEncoder.encode("paciente-seguro-123")).thenReturn("hash-encriptado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDetails userDetails = User.builder().username("juan@clinica.test").password("hash-encriptado")
                .authorities(List.of(() -> "ROLE_PACIENTE")).build();
        when(userDetailsService.loadUserByUsername("juan@clinica.test")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("un-token-jwt");
    }

    @Test
    void registrarPaciente_crea_usuario_y_perfil_y_devuelve_token() {
        prepararAltaExitosa();

        AuthResponse respuesta = authService.registrarPaciente(registroValido(), IP);

        assertThat(respuesta.token()).isEqualTo("un-token-jwt");
        assertThat(respuesta.email()).isEqualTo("juan@clinica.test");
        assertThat(respuesta.rol()).isEqualTo(Rol.PACIENTE);
    }

    @Test
    void registrarPaciente_deja_la_identidad_sin_verificar() {
        prepararAltaExitosa();

        authService.registrarPaciente(registroValido(), IP);

        ArgumentCaptor<Paciente> guardado = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository).save(guardado.capture());
        assertThat(guardado.getValue().isIdentidadVerificada()).isFalse();
        assertThat(guardado.getValue().getDni()).isEqualTo("30111222");
    }

    @Test
    void registrarPaciente_no_revela_si_el_dni_ya_pertenece_a_un_paciente() {
        // Aunque ya haya un paciente con ese DNI, el alta se acepta (queda sin verificar) y ni siquiera
        // se consulta el DNI: preguntar "¿existe?" es justamente lo que permitiría enumerar pacientes.
        prepararAltaExitosa();

        authService.registrarPaciente(registroValido(), IP);

        verify(pacienteRepository, never()).existsByDniAndIdentidadVerificadaTrue(any());
        verify(pacienteRepository, never()).findByDniAndIdentidadVerificadaTrue(any());
    }

    @Test
    void registrarPaciente_rechaza_email_duplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(true);
        assertStatus(() -> authService.registrarPaciente(registroValido(), IP), HttpStatus.CONFLICT);
    }

    @Test
    void registrarPaciente_respeta_el_limite_de_altas_por_ip_y_no_crea_nada_si_se_pasa() {
        doThrow(new DemasiadosIntentosException(600)).when(loginAttemptService).verificarRegistroPermitido(IP);

        assertStatus(() -> authService.registrarPaciente(registroValido(), IP), HttpStatus.TOO_MANY_REQUESTS);

        verifyNoInteractions(usuarioRepository, pacienteRepository);
    }

    @Test
    void login_exitoso_devuelve_token_y_borra_los_fallos_previos() {
        Usuario usuario = Usuario.builder().id(1L).email("admin@clinica.test").passwordHash("hash")
                .rol(Rol.ADMIN).nombre("Admin").apellido("Clínica").activo(true).build();
        when(usuarioRepository.findByEmailIgnoreCase("admin@clinica.test")).thenReturn(Optional.of(usuario));

        UserDetails userDetails = User.builder().username("admin@clinica.test").password("hash")
                .authorities(List.of(() -> "ROLE_ADMIN")).build();
        when(userDetailsService.loadUserByUsername("admin@clinica.test")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("token-admin");

        AuthResponse respuesta = authService.login(new LoginRequest("admin@clinica.test", "una-clave-valida"), IP);

        assertThat(respuesta.token()).isEqualTo("token-admin");
        assertThat(respuesta.rol()).isEqualTo(Rol.ADMIN);
        verify(loginAttemptService).registrarExito("admin@clinica.test");
        verify(loginAttemptService, never()).registrarFallo(any(), any());
    }

    @Test
    void login_rechaza_credenciales_invalidas_y_anota_el_fallo() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));

        assertStatus(() -> authService.login(new LoginRequest("x@clinica.test", "mal"), IP), HttpStatus.UNAUTHORIZED);

        verify(loginAttemptService).registrarFallo("x@clinica.test", IP);
        verify(loginAttemptService, never()).registrarExito(any());
    }

    @Test
    void login_bloqueado_por_demasiados_intentos_ni_siquiera_prueba_la_contrasena() {
        doThrow(new DemasiadosIntentosException(300)).when(loginAttemptService).verificarLoginPermitido("x@clinica.test", IP);

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@clinica.test", "la-clave-correcta"), IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        verifyNoInteractions(authenticationManager);
        verify(loginAttemptService, never()).registrarFallo(any(), any());
    }
}
