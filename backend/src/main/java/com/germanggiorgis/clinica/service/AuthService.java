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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    /** Autorregistro público: siempre crea una cuenta PACIENTE. */
    public AuthResponse registrarPaciente(RegistroPacienteRequest request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese email");
        }
        if (pacienteRepository.findByDni(request.dni()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un paciente con ese DNI");
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(Rol.PACIENTE)
                .nombre(request.nombre())
                .apellido(request.apellido())
                .telefono(request.telefono())
                .build());

        pacienteRepository.save(Paciente.builder()
                .usuario(usuario)
                .dni(request.dni())
                .fechaNacimiento(request.fechaNacimiento())
                .build());

        return tokenPara(usuario);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
        }

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos"));
        return tokenPara(usuario);
    }

    private AuthResponse tokenPara(Usuario usuario) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, usuario.getEmail(), usuario.getNombre(), usuario.getApellido(), usuario.getRol());
    }
}
