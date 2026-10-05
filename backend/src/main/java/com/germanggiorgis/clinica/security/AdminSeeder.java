package com.germanggiorgis.clinica.security;

import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import com.germanggiorgis.clinica.validation.ClaveValidaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el primer usuario ADMIN si todavía no existe ninguno (sin eso nadie podría dar de alta
 * médicos, porque esa operación requiere ser ADMIN). Nunca con una contraseña conocida de antemano:
 * <ol>
 *   <li>Si se definen {@code ADMIN_EMAIL} y {@code ADMIN_PASSWORD}, se usa ese admin. Es lo que hay
 *       que hacer en cualquier entorno real. La contraseña tiene que cumplir la política de claves
 *       y no se escribe en el log.</li>
 *   <li>Si no, y solo si se activa {@code SEED_DEMO_ADMIN=true} (lo trae
 *       application-local.properties.example, o sea, solo desarrollo local), se crea un admin de
 *       demostración con credenciales públicas del README.</li>
 *   <li>Si no, no se crea nada y se avisa en el log qué falta.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    static final String EMAIL_DEMO = "admin@clinica.test";
    static final String PASSWORD_DEMO = "admin1234";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${clinica.admin.email:}")
    private String adminEmail;

    @Value("${clinica.admin.password:}")
    private String adminPassword;

    @Value("${clinica.seed.demo-admin:false}")
    private boolean demoAdmin;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRol(Rol.ADMIN)) {
            return;
        }

        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            if (!ClaveValidaValidator.esValida(adminPassword)) {
                throw new IllegalStateException("ADMIN_PASSWORD no cumple la política: al menos "
                        + ClaveValidaValidator.MINIMO_CARACTERES + " caracteres y no más de "
                        + ClaveValidaValidator.MAXIMO_BYTES + " bytes");
            }
            crearAdmin(adminEmail.trim(), adminPassword);
            log.info("Usuario admin inicial creado: {}", adminEmail.trim());
        } else if (demoAdmin) {
            crearAdmin(EMAIL_DEMO, PASSWORD_DEMO);
            log.warn("Usuario admin de DEMOSTRACIÓN creado ({}, contraseña pública en el README). "
                    + "Solo para desarrollo local: en un entorno real definí ADMIN_EMAIL y ADMIN_PASSWORD "
                    + "y no actives SEED_DEMO_ADMIN.", EMAIL_DEMO);
        } else {
            log.warn("No existe ningún usuario ADMIN y no se creó ninguno: definí ADMIN_EMAIL y ADMIN_PASSWORD "
                    + "y reiniciá (o SEED_DEMO_ADMIN=true, solo para desarrollo local).");
        }
    }

    private void crearAdmin(String email, String password) {
        usuarioRepository.save(Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(Rol.ADMIN)
                .nombre("Admin")
                .apellido("Clínica")
                .build());
    }
}
