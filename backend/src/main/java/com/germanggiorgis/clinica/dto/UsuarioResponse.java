package com.germanggiorgis.clinica.dto;

import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;

public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        String telefono,
        Rol rol,
        boolean activo,
        /** Id de su Paciente o Medico (null para ASISTENTE/ADMIN, que no tienen perfil propio). */
        Long perfilId
) {
    public static UsuarioResponse from(Usuario usuario) {
        return from(usuario, null);
    }

    public static UsuarioResponse from(Usuario usuario, Long perfilId) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getTelefono(),
                usuario.getRol(),
                usuario.isActivo(),
                perfilId
        );
    }
}
