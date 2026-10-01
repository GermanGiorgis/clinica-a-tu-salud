package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo usa un ADMIN para crear cualquier tipo de cuenta. Según el rol hacen
 * falta campos distintos (ver UsuarioService): PACIENTE pide dni y
 * fechaNacimiento; MEDICO pide matricula y, opcionalmente, especialidadIds;
 * ADMIN no tiene datos propios.
 */
public record CrearUsuarioRequest(
        @NotNull Rol rol,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String password,
        @Size(max = 30) String telefono,

        // Solo PACIENTE
        String dni,
        LocalDate fechaNacimiento,

        // Solo MEDICO
        String matricula,
        List<Long> especialidadIds
) {
}
