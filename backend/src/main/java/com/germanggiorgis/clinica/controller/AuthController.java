package com.germanggiorgis.clinica.controller;

import com.germanggiorgis.clinica.dto.AuthResponse;
import com.germanggiorgis.clinica.dto.LoginRequest;
import com.germanggiorgis.clinica.dto.RegistroPacienteRequest;
import com.germanggiorgis.clinica.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registro(@Valid @RequestBody RegistroPacienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrarPaciente(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
