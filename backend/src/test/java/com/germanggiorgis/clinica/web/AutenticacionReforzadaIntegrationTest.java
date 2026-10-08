package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Política de contraseñas, límite de intentos contra la fuerza bruta y desactivación de cuentas.
 * El límite de intentos es por IP y todos los tests comparten la 127.0.0.1, así que cada test simula
 * una IP propia con {@link #desde}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/clinica_test",
        "jwt.secret=secreto-solo-para-tests-no-es-un-secreto-real-0123456789abcdef"
})
class AutenticacionReforzadaIntegrationTest extends BaseIntegracionConDatos {

    private static final String CLAVE = "clave-correcta-12345";

    private MockHttpServletRequestBuilder login(String email, String password, String ip) {
        return post("/api/auth/login").with(desde(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private MockHttpServletRequestBuilder registro(String password, String ip) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return post("/api/auth/registro").with(desde(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nombre\",\"apellido\":\"Apellido\",\"email\":\"alta-" + s + DOMINIO + "\",\"password\":\"" + password
                        + "\",\"dni\":\"" + (100_000_000L + (long) (Math.random() * 8e8)) + "\",\"fechaNacimiento\":\"1990-01-01\"}");
    }

    // --- política de contraseñas ---

    @Test
    void una_contrasena_de_menos_de_10_caracteres_se_rechaza_con_400_y_dice_por_que() throws Exception {
        mockMvc.perform(registro("123456789", "10.60.0.1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.password").value(containsString("10 caracteres")));
    }

    @Test
    void una_contrasena_que_pasa_de_72_bytes_da_400_y_no_el_500_de_bcrypt() throws Exception {
        mockMvc.perform(registro("a".repeat(73), "10.60.0.2")).andExpect(status().isBadRequest());
        mockMvc.perform(registro("a".repeat(100), "10.60.0.2")).andExpect(status().isBadRequest());
    }

    @Test
    void una_contrasena_valida_se_acepta() throws Exception {
        mockMvc.perform(registro("a".repeat(72), "10.60.0.3")).andExpect(status().isCreated());
        mockMvc.perform(registro("1234567890", "10.60.0.3")).andExpect(status().isCreated());
    }

    @Test
    void el_admin_tampoco_puede_crear_una_cuenta_con_una_contrasena_debil() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);

        mockMvc.perform(post("/api/usuarios").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"ADMIN\",\"nombre\":\"x\",\"apellido\":\"y\",\"email\":\"debil-" + UUID.randomUUID() + DOMINIO
                                + "\",\"password\":\"admin1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.password").exists());
    }

    @Test
    void los_datos_demasiado_largos_dan_400_en_lugar_de_romper_la_base() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);

        mockMvc.perform(post("/api/usuarios").header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"PACIENTE\",\"nombre\":\"x\",\"apellido\":\"y\",\"email\":\"largo-" + UUID.randomUUID() + DOMINIO
                                + "\",\"password\":\"" + CLAVE + "\",\"dni\":\"" + "9".repeat(40) + "\",\"fechaNacimiento\":\"1990-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.dni").exists());
    }

    @Test
    void una_contrasena_de_login_gigante_se_rechaza_sin_gastar_bcrypt() throws Exception {
        mockMvc.perform(login("alguien@qa.test", "a".repeat(5000), "10.60.0.4")).andExpect(status().isBadRequest());
    }

    // --- fuerza bruta ---

    @Test
    void despues_de_5_intentos_fallidos_la_cuenta_se_bloquea_aunque_llegue_la_contrasena_correcta() throws Exception {
        Usuario victima = usuario(Rol.PACIENTE, CLAVE);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(victima.getEmail(), "mala-" + i, "10.61.0.1")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(victima.getEmail(), CLAVE, "10.61.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value(containsString("Demasiados intentos")));
    }

    @Test
    void el_bloqueo_sigue_aunque_el_atacante_cambie_de_ip_porque_es_por_cuenta() throws Exception {
        Usuario victima = usuario(Rol.PACIENTE, CLAVE);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(victima.getEmail(), "mala-" + i, "10.62.0." + i)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(victima.getEmail(), CLAVE, "10.62.1.1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void el_bloqueo_de_una_cuenta_no_afecta_a_las_demas() throws Exception {
        Usuario victima = usuario(Rol.PACIENTE, CLAVE);
        Usuario otra = usuario(Rol.PACIENTE, CLAVE);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(victima.getEmail(), "mala-" + i, "10.63.0.1")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(otra.getEmail(), CLAVE, "10.63.0.1")).andExpect(status().isOk());
    }

    @Test
    void un_login_correcto_borra_los_fallos_anteriores() throws Exception {
        Usuario usuario = usuario(Rol.PACIENTE, CLAVE);
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(usuario.getEmail(), "mala-" + i, "10.64.0.1")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(usuario.getEmail(), CLAVE, "10.64.0.1")).andExpect(status().isOk());

        // Si los 4 fallos anteriores siguieran contando, el 5.º de ahora ya bloquearía.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(usuario.getEmail(), "otra-" + i, "10.64.0.1")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(usuario.getEmail(), CLAVE, "10.64.0.1")).andExpect(status().isOk());
    }

    @Test
    void el_registro_masivo_desde_una_ip_se_corta_a_las_10_altas_por_hora() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(registro(CLAVE, "10.65.0.1")).andExpect(status().isCreated());
        }

        mockMvc.perform(registro(CLAVE, "10.65.0.1")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mockMvc.perform(registro(CLAVE, "10.65.0.2")).andExpect(status().isCreated());
    }

    // --- desactivar cuentas ---

    @Test
    void un_admin_desactiva_a_un_medico_y_su_token_deja_de_servir_de_inmediato() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        Medico medico = medico();
        String tokenDelMedico = token(medico.getUsuario());
        mockMvc.perform(get("/api/turnos/agenda").header("Authorization", tokenDelMedico)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/usuarios/" + medico.getUsuario().getId() + "/activo").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(get("/api/turnos/agenda").header("Authorization", tokenDelMedico)).andExpect(status().isUnauthorized());
        mockMvc.perform(login(medico.getUsuario().getEmail(), "clave-de-prueba-1234", "10.66.0.1")).andExpect(status().isUnauthorized());
    }

    @Test
    void una_cuenta_desactivada_se_puede_reactivar() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);
        Medico medico = medico();
        mockMvc.perform(patch("/api/usuarios/" + medico.getUsuario().getId() + "/activo").header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());

        mockMvc.perform(patch("/api/usuarios/" + medico.getUsuario().getId() + "/activo").header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/turnos/agenda").header("Authorization", token(medico.getUsuario()))).andExpect(status().isOk());
    }

    @Test
    void un_admin_no_puede_desactivar_su_propia_cuenta() throws Exception {
        Usuario admin = usuario(Rol.ADMIN);

        mockMvc.perform(patch("/api/usuarios/" + admin.getId() + "/activo").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.findById(admin.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void solo_un_admin_puede_activar_o_desactivar_cuentas() throws Exception {
        Medico medico = medico();
        String cuerpo = "{\"activo\":false}";

        mockMvc.perform(patch("/api/usuarios/" + medico.getUsuario().getId() + "/activo").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/usuarios/" + medico.getUsuario().getId() + "/activo").header("Authorization", token(medico.getUsuario()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/usuarios/" + medico.getUsuario().getId() + "/activo").header("Authorization", token(paciente().getUsuario()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
    }
}
