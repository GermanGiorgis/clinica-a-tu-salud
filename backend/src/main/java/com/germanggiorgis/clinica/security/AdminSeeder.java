package com.germanggiorgis.clinica.security;

import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea un usuario ADMIN de demostración en el primer arranque, si todavía no
 * existe ninguno -- sin esto nadie podría crear médicos o asistentes, porque
 * esa operación requiere ser ADMIN. Es un dato de arranque para desarrollo y
 * demo, no para producción real (ahí se crearía a mano una sola vez y se
 * cambiaría la contraseña).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private static final String EMAIL_DEMO = "admin@clinica.test";
    private static final String PASSWORD_DEMO = "admin1234";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRol(Rol.ADMIN)) {
            return;
        }

        Usuario admin = Usuario.builder()
                .email(EMAIL_DEMO)
                .passwordHash(passwordEncoder.encode(PASSWORD_DEMO))
                .rol(Rol.ADMIN)
                .nombre("Admin")
                .apellido("Clínica")
                .build();
        usuarioRepository.save(admin);

        log.warn("Usuario admin de demo creado -> email: {} / password: {} (cambiar en producción)",
                EMAIL_DEMO, PASSWORD_DEMO);
    }
}
