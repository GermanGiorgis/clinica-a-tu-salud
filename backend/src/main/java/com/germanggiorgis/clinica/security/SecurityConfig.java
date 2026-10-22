package com.germanggiorgis.clinica.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Catálogos: lectura (GET) pública -- hace falta para que un paciente sin
 * login pueda ver médicos/especialidades/horarios antes de registrarse --,
 * escritura solo ADMIN. /api/turnos/** requiere estar logueado; quién puede
 * hacer qué con sus turnos se valida en TurnoService (dueño del turno,
 * médico del turno, o admin), no acá.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${cors.allowed-origin}")
    private String allowedOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Un rechazo de Spring Security (filtro, antes de llegar a los
                // controllers) no pasa por GlobalExceptionHandler; sin esto,
                // un 401/403 del filtro vendría con el whitelabel error de
                // Spring Boot en vez del mismo {"error": "..."} del resto de la API.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Map.of("error", "Necesitás iniciar sesión")));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Map.of("error", "No tenés permiso para hacer esto")));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // /api/demo/** se autentica solo, con un token propio (ver DemoController); si el reinicio está apagado, responde 404.
                        .requestMatchers("/api/auth/**", "/api/demo/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/especialidades/**", "/api/consultorios/**",
                                "/api/motivos-cancelacion/**", "/api/horarios-atencion/**",
                                "/api/medicos/**", "/api/patologias/**", "/api/medicamentos/**",
                                "/api/obras-sociales/**", "/api/estudios/**").permitAll()
                        .requestMatchers("/api/usuarios/**", "/api/metricas/**", "/api/auditoria/**").hasRole("ADMIN")
                        // Mostrador: verificar la identidad de los pacientes y cargar feriados/ausencias.
                        .requestMatchers("/api/pacientes/**", "/api/dias-no-laborables/**").hasAnyRole("ADMIN", "RECEPCIONISTA")
                        .requestMatchers(HttpMethod.POST, "/api/especialidades/**", "/api/consultorios/**",
                                "/api/motivos-cancelacion/**", "/api/horarios-atencion/**",
                                "/api/patologias/**", "/api/medicamentos/**", "/api/obras-sociales/**",
                                "/api/estudios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/especialidades/**", "/api/consultorios/**",
                                "/api/motivos-cancelacion/**", "/api/horarios-atencion/**",
                                "/api/patologias/**", "/api/medicamentos/**", "/api/obras-sociales/**",
                                "/api/estudios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/especialidades/**", "/api/consultorios/**",
                                "/api/motivos-cancelacion/**", "/api/horarios-atencion/**",
                                "/api/patologias/**", "/api/medicamentos/**", "/api/obras-sociales/**",
                                "/api/estudios/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
