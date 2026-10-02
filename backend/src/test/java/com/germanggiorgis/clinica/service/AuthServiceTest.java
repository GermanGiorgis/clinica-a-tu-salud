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
import com.germanggiorgis.clinica.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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

    @InjectMocks
    private AuthService authService;

    private RegistroPacienteRequest registroValido() {
        return new RegistroPacienteRequest("Juan", "Gómez", "juan@clinica.test", "paciente123",
                null, "30111222", LocalDate.of(1990, 5, 20));
    }

    @Test
    void registrarPaciente_crea_usuario_y_perfil_y_devuelve_token() {
        RegistroPacienteRequest request = registroValido();
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(false);
        when(pacienteRepository.findByDni("30111222")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("paciente123")).thenReturn("hash-encriptado");
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

        AuthResponse respuesta = authService.registrarPaciente(request);

        assertThat(respuesta.token()).isEqualTo("un-token-jwt");
        assertThat(respuesta.email()).isEqualTo("juan@clinica.test");
        assertThat(respuesta.rol()).isEqualTo(Rol.PACIENTE);
    }

    @Test
    void registrarPaciente_rechaza_email_duplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(true);
        assertStatus(() -> authService.registrarPaciente(registroValido()), HttpStatus.CONFLICT);
    }

    @Test
    void registrarPaciente_rechaza_dni_duplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("juan@clinica.test")).thenReturn(false);
        when(pacienteRepository.findByDni("30111222")).thenReturn(Optional.of(
                Paciente.builder().id(5L).dni("30111222").fechaNacimiento(LocalDate.now()).build()));

        assertStatus(() -> authService.registrarPaciente(registroValido()), HttpStatus.CONFLICT);
    }

    @Test
    void login_exitoso_devuelve_token() {
        Usuario usuario = Usuario.builder().id(1L).email("admin@clinica.test").passwordHash("hash")
                .rol(Rol.ADMIN).nombre("Admin").apellido("Clínica").activo(true).build();
        when(usuarioRepository.findByEmailIgnoreCase("admin@clinica.test")).thenReturn(Optional.of(usuario));

        UserDetails userDetails = User.builder().username("admin@clinica.test").password("hash")
                .authorities(List.of(() -> "ROLE_ADMIN")).build();
        when(userDetailsService.loadUserByUsername("admin@clinica.test")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("token-admin");

        AuthResponse respuesta = authService.login(new LoginRequest("admin@clinica.test", "admin1234"));

        assertThat(respuesta.token()).isEqualTo("token-admin");
        assertThat(respuesta.rol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void login_rechaza_credenciales_invalidas() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));
        assertStatus(() -> authService.login(new LoginRequest("x@clinica.test", "mal")), HttpStatus.UNAUTHORIZED);
    }
}
