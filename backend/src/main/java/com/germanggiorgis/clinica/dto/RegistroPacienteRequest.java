package com.germanggiorgis.clinica.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import com.germanggiorgis.clinica.validation.ClaveValida;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Autorregistro público: solo crea cuentas de PACIENTE. */
public record RegistroPacienteRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @ClaveValida String password,
        @Size(max = 30) String telefono,
        @NotBlank @Pattern(regexp = "\\d{6,9}", message = "El DNI tiene que tener entre 6 y 9 números, sin puntos ni espacios") String dni,
        @NotNull @Past LocalDate fechaNacimiento
) {
}
