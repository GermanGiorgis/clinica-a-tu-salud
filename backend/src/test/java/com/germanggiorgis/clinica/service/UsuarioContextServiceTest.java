package com.germanggiorgis.clinica.service;

import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static com.germanggiorgis.clinica.service.HttpStatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioContextServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioContextService usuarioContextService;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    @Test
    void devuelve_el_usuario_del_contexto_de_seguridad() {
        Usuario usuario = Usuario.builder().id(1L).email("juan@clinica.test").rol(Rol.PACIENTE)
                .nombre("Juan").apellido("Gómez").activo(true).build();
        autenticarComo("juan@clinica.test");
        when(usuarioRepository.findByEmailIgnoreCase("juan@clinica.test")).thenReturn(Optional.of(usuario));

        assertThat(usuarioContextService.getUsuarioActual()).isSameAs(usuario);
    }

    @Test
    void rechaza_un_token_valido_de_un_usuario_que_ya_no_existe() {
        autenticarComo("fantasma@clinica.test");
        when(usuarioRepository.findByEmailIgnoreCase("fantasma@clinica.test")).thenReturn(Optional.empty());

        assertStatus(() -> usuarioContextService.getUsuarioActual(), HttpStatus.UNAUTHORIZED);
    }
}
