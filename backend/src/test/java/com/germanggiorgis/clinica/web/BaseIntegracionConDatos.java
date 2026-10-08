package com.germanggiorgis.clinica.web;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Paciente;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultaRepository;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.MotivoCancelacionRepository;
import com.germanggiorgis.clinica.repository.PacienteRepository;
import com.germanggiorgis.clinica.repository.TurnoRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import com.germanggiorgis.clinica.security.CustomUserDetailsService;
import com.germanggiorgis.clinica.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base para los tests de integración que necesitan commits REALES (sin
 * {@code @Transactional}): concurrencia entre hilos, o errores que solo aparecen al
 * hacer commit (restricciones de la base). Como no hay rollback automático, todo lo que
 * se crea lleva una marca ("@qa.test" en el email, "QA " en los nombres) y
 * {@link #limpiar()} lo borra por SQL al terminar cada test.
 *
 * <p>Las subclases llevan las anotaciones de Spring (@SpringBootTest, @AutoConfigureMockMvc y
 * @TestPropertySource), porque no todas se heredan.
 */
abstract class BaseIntegracionConDatos {

    protected static final String DOMINIO = "@qa.test";
    protected static final String PREFIJO = "QA ";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected PacienteRepository pacienteRepository;
    @Autowired protected MedicoRepository medicoRepository;
    @Autowired protected ConsultorioRepository consultorioRepository;
    @Autowired protected HorarioAtencionRepository horarioRepository;
    @Autowired protected TurnoRepository turnoRepository;
    @Autowired protected ConsultaRepository consultaRepository;
    @Autowired protected MotivoCancelacionRepository motivoRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;
    @Autowired protected CustomUserDetailsService userDetailsService;
    @Autowired protected JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        String medicos = "(SELECT m.id FROM medico m JOIN usuario u ON u.id = m.usuario_id WHERE u.email LIKE '%" + DOMINIO + "')";
        String pacientes = "(SELECT p.id FROM paciente p JOIN usuario u ON u.id = p.usuario_id WHERE u.email LIKE '%" + DOMINIO + "')";
        String turnos = "(SELECT id FROM turno WHERE medico_id IN " + medicos + " OR paciente_id IN " + pacientes + ")";
        jdbc.update("DELETE FROM acceso_historial WHERE usuario_id IN (SELECT id FROM usuario WHERE email LIKE '%" + DOMINIO + "') OR paciente_id IN " + pacientes);
        jdbc.update("DELETE FROM dia_no_laborable WHERE descripcion LIKE '" + PREFIJO + "%' OR medico_id IN " + medicos
                + " OR creado_por IN (SELECT id FROM usuario WHERE email LIKE '%" + DOMINIO + "')");
        jdbc.update("DELETE FROM item_factura WHERE factura_id IN (SELECT id FROM factura WHERE turno_id IN " + turnos + ")");
        jdbc.update("DELETE FROM factura WHERE turno_id IN " + turnos);
        jdbc.update("DELETE FROM encuesta WHERE turno_id IN " + turnos);
        jdbc.update("DELETE FROM diagnostico WHERE consulta_id IN (SELECT id FROM consulta WHERE turno_id IN " + turnos + ")");
        jdbc.update("DELETE FROM consulta WHERE turno_id IN " + turnos);
        jdbc.update("DELETE FROM turno WHERE id IN " + turnos);
        jdbc.update("DELETE FROM horario_atencion WHERE medico_id IN " + medicos);
        jdbc.update("DELETE FROM medico_especialidad WHERE medico_id IN " + medicos);
        jdbc.update("DELETE FROM medico WHERE id IN " + medicos);
        jdbc.update("DELETE FROM especialidad WHERE nombre LIKE '" + PREFIJO + "%'");
        jdbc.update("DELETE FROM afiliacion WHERE paciente_id IN " + pacientes);
        jdbc.update("DELETE FROM paciente WHERE id IN " + pacientes);
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%" + DOMINIO + "'");
        jdbc.update("DELETE FROM consultorio WHERE nombre LIKE '" + PREFIJO + "%'");
        jdbc.update("DELETE FROM motivo_cancelacion WHERE nombre LIKE '" + PREFIJO + "%'");
    }

    protected Usuario usuario(Rol rol) {
        return usuario(rol, "clave-de-prueba-1234");
    }

    protected Usuario usuario(Rol rol, String password) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return usuarioRepository.saveAndFlush(Usuario.builder().email(rol.name().toLowerCase() + "-" + s + DOMINIO)
                .passwordHash(passwordEncoder.encode(password)).rol(rol).nombre("N" + s).apellido("A").build());
    }

    /** Paciente con la identidad ya verificada por la clinica (lo normal para los tests de reservas). */
    protected Paciente paciente() {
        return paciente(true);
    }

    protected Paciente paciente(boolean identidadVerificada) {
        return pacienteRepository.saveAndFlush(Paciente.builder().usuario(usuario(Rol.PACIENTE))
                .dni(String.valueOf(10_000_000 + (int) (Math.random() * 80_000_000))).fechaNacimiento(LocalDate.of(1990, 1, 1))
                .identidadVerificada(identidadVerificada).build());
    }

    protected Medico medico() {
        return medicoRepository.saveAndFlush(Medico.builder().usuario(usuario(Rol.MEDICO))
                .matricula(UUID.randomUUID().toString().substring(0, 12)).build());
    }

    protected Consultorio consultorio() {
        return consultorioRepository.saveAndFlush(Consultorio.builder().nombre(PREFIJO + UUID.randomUUID()).build());
    }

    /** Horario de 08:00 a 20:00 en turnos de 30 minutos, el día de la semana de {@code fecha}. */
    protected HorarioAtencion horario(Medico medico, Consultorio consultorio, LocalDate fecha) {
        return horarioRepository.saveAndFlush(HorarioAtencion.builder().medico(medico).consultorio(consultorio)
                .diaSemana(fecha.getDayOfWeek()).horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(20, 0))
                .duracionTurnoMin(30).vigenteDesde(LocalDate.now().minusDays(30)).build());
    }

    protected String token(Usuario usuario) {
        return "Bearer " + jwtService.generateToken(userDetailsService.loadUserByUsername(usuario.getEmail()));
    }

    protected MockHttpServletRequestBuilder reservar(Usuario quien, Medico medico, LocalDateTime inicio) {
        return post("/api/turnos").header("Authorization", token(quien)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicoId\":" + medico.getId() + ",\"inicio\":\"" + inicio + "\"}");
    }

    /** Hace que el pedido parezca venir de esa IP: el limite de intentos es por IP y todos los tests comparten la 127.0.0.1. */
    protected static RequestPostProcessor desde(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected static int[] ordenados(int[] estados) {
        int[] copia = estados.clone();
        java.util.Arrays.sort(copia);
        return copia;
    }

    /** Estado HTTP; una excepción que se escapa del controller cuenta como 500, igual que en HTTP real. */
    protected int estado(MockHttpServletRequestBuilder request) {
        try {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return 500;
        }
    }

    /** Lanza las dos llamadas a la vez (con una barrera) y devuelve sus dos estados HTTP, en el mismo orden que los pedidos. */
    protected int[] enParalelo(MockHttpServletRequestBuilder a, MockHttpServletRequestBuilder b) throws Exception {
        return enParalelo(List.of(a, b));
    }

    /** Igual que arriba pero con tantos pedidos como se quiera, todos largados a la vez. */
    protected int[] enParalelo(List<MockHttpServletRequestBuilder> pedidos) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(pedidos.size());
        try {
            CountDownLatch listos = new CountDownLatch(pedidos.size());
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Integer>> futuros = new ArrayList<>();
            for (MockHttpServletRequestBuilder request : pedidos) {
                Callable<Integer> tarea = () -> {
                    listos.countDown();
                    largada.await();
                    return estado(request);
                };
                futuros.add(pool.submit(tarea));
            }
            listos.await();
            largada.countDown();
            int[] estados = new int[pedidos.size()];
            for (int i = 0; i < estados.length; i++) {
                estados[i] = futuros.get(i).get();
            }
            return estados;
        } finally {
            pool.shutdown();
        }
    }
}
