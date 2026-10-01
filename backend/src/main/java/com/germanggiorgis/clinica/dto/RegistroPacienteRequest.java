package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Autorregistro público: solo crea cuentas de PACIENTE. */
public record RegistroPacienteRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String password,
        @Size(max = 30) String telefono,
        @NotBlank @Size(max = 20) String dni,
        @NotNull @Past LocalDate fechaNacimiento
) {
}
