package com.germanggiorgis.clinica.security;

import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminSeeder seeder;

    private void configurar(String email, String password, boolean demo) {
        ReflectionTestUtils.setField(seeder, "adminEmail", email);
        ReflectionTestUtils.setField(seeder, "adminPassword", password);
        ReflectionTestUtils.setField(seeder, "demoAdmin", demo);
    }

    @Test
    void si_ya_hay_un_admin_no_hace_nada_aunque_haya_credenciales_configuradas() {
        configurar("otro@clinica.com", "una-clave-larga-123", true);
        when(usuarioRepository.existsByRol(Rol.ADMIN)).thenReturn(true);

        seeder.run(null);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void con_credenciales_configuradas_crea_ese_admin_con_la_clave_hasheada() {
        configurar("  jefa@clinica.com ", "una-clave-larga-123", false);
        when(usuarioRepository.existsByRol(Rol.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("una-clave-larga-123")).thenReturn("hash-bcrypt");

        seeder.run(null);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getEmail()).isEqualTo("jefa@clinica.com");
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(guardado.getValue().getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void con_una_clave_que_no_cumple_la_politica_el_arranque_falla_y_no_crea_nada() {
        configurar("jefa@clinica.com", "corta", false);
        when(usuarioRepository.existsByRol(Rol.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_PASSWORD");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void las_credenciales_configuradas_tienen_prioridad_sobre_el_admin_de_demostracion() {
        configurar("jefa@clinica.com", "una-clave-larga-123", true);
        when(usuarioRepository.existsByRol(Rol.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("una-clave-larga-123")).thenReturn("hash");

        seeder.run(null);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getEmail()).isEqualTo("jefa@clinica.com");
    }

    @Test
    void solo_con_el_modo_demo_explicito_crea_el_admin_de_demostracion() {
        configurar("", "", true);
        when(usuarioRepository.existsByRol(Rol.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(AdminSeeder.PASSWORD_DEMO)).thenReturn("hash-demo");

        seeder.run(null);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getEmail()).isEqualTo(AdminSeeder.EMAIL_DEMO);
        assertThat(guardado.getValue().getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void sin_credenciales_ni_modo_demo_no_crea_ningun_admin_con_clave_conocida() {
        configurar("", "", false);
        when(usuarioRepository.existsByRol(Rol.ADMIN)).thenReturn(false);

        seeder.run(null);

        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void con_solo_el_email_o_solo_la_clave_configurados_no_se_crea_un_admin_a_medias() {
        when(usuarioRepository.existsByRol(Rol.ADMIN)).thenReturn(false);

        configurar("jefa@clinica.com", "", false);
        seeder.run(null);
        configurar("", "una-clave-larga-123", false);
        seeder.run(null);

        verify(usuarioRepository, never()).save(any());
    }
}
