package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.config.ReinicioDeDemostracion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Reinicio diario de la demo en línea (ver {@link ReinicioDeDemostracion}). Es un GET porque el cron de Vercel solo sabe
 * hacer GET. No usa la sesión de la aplicación: se autentica con el secreto del cron. Si el reinicio está apagado
 * (cualquier entorno que no sea la demo) responde 404, como si la ruta no existiera.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final ReinicioDeDemostracion reinicio;

    @GetMapping("/reiniciar")
    public ResponseEntity<Map<String, String>> reiniciar(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!reinicio.estaHabilitado()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No se encontró lo que buscás"));
        }
        if (!reinicio.tokenValido(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credencial inválida"));
        }
        reinicio.reiniciar();
        return ResponseEntity.ok(Map.of("estado", "reiniciado"));
    }
}
