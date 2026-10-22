package com.germanggiorgis.clinica.config;

import com.germanggiorgis.clinica.security.AdminSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Reinicia la demo en línea: cualquiera puede escribir en una demo pública, así que una vez por día se borra TODO (Flyway
 * `clean`), se vuelve a crear el esquema y se cargan de nuevo los datos de demostración. Lo dispara el cron de Vercel con
 * `Authorization: Bearer $CRON_SECRET` contra {@code GET /api/demo/reiniciar}.
 *
 * <p>Es peligroso a propósito, así que tiene dos candados independientes: {@code DEMO_REINICIO=true} (esta clase) y
 * {@code FLYWAY_CLEAN_DISABLED=false} (Flyway se niega a borrar si no). Con cualquiera de los dos apagado, y siempre sin
 * un {@code CRON_SECRET} definido, no hay nada que llamar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReinicioDeDemostracion {

    private static final String PREFIJO = "Bearer ";

    private final Flyway flyway;
    private final AdminSeeder adminSeeder;
    private final CuentasDeDemostracion cuentas;
    private final MedicosDeDemostracion medicos;

    @Value("${clinica.demo.reinicio:false}")
    private boolean habilitado;

    @Value("${clinica.demo.reinicio-token:}")
    private String token;

    public boolean estaHabilitado() {
        return habilitado && !token.isBlank();
    }

    /** ¿El encabezado Authorization trae "Bearer " + el token configurado? Se compara en tiempo constante. */
    public boolean tokenValido(String authorization) {
        if (!estaHabilitado() || authorization == null || !authorization.startsWith(PREFIJO)) {
            return false;
        }
        return MessageDigest.isEqual(
                authorization.substring(PREFIJO.length()).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    /** Una sola a la vez: dos reinicios simultáneos se pisarían a mitad del borrado. */
    public synchronized void reiniciar() {
        if (!estaHabilitado()) {
            throw new IllegalStateException("El reinicio de la demostración está apagado");
        }
        log.warn("Reiniciando la demostración: se borra toda la base y se vuelve a cargar.");
        flyway.clean();
        flyway.migrate();
        adminSeeder.run(null);
        cuentas.run(null);
        medicos.run(null);
        log.warn("Demostración reiniciada.");
    }
}
