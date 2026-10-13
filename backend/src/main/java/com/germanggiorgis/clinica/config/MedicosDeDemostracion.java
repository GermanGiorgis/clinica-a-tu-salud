package com.germanggiorgis.clinica.config;

import com.germanggiorgis.clinica.model.Consultorio;
import com.germanggiorgis.clinica.model.Especialidad;
import com.germanggiorgis.clinica.model.HorarioAtencion;
import com.germanggiorgis.clinica.model.Medico;
import com.germanggiorgis.clinica.model.Rol;
import com.germanggiorgis.clinica.model.Usuario;
import com.germanggiorgis.clinica.repository.ConsultorioRepository;
import com.germanggiorgis.clinica.repository.EspecialidadRepository;
import com.germanggiorgis.clinica.repository.HorarioAtencionRepository;
import com.germanggiorgis.clinica.repository.MedicoRepository;
import com.germanggiorgis.clinica.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Carga profesionales de demostración para que cada especialidad del catálogo tenga a quién pedirle turno:
 * dos por especialidad (uno atiende a la mañana y el otro a la tarde, en el mismo consultorio) y algunos
 * con más de una especialidad. Solo para desarrollo local ({@code SEED_DEMO_MEDICOS=true}, lo trae
 * application-local.properties.example): son personas inventadas, y en un entorno real los médicos los da de
 * alta el administrador.
 *
 * <p>Ninguna de estas cuentas tiene una contraseña que se conozca: sirven para reservar turnos, no para
 * iniciar sesión. Para entrar como médico, el administrador crea una cuenta desde la pantalla Usuarios.
 * Es idempotente: lo que ya existe no se repite, así que se puede dejar activado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MedicosDeDemostracion implements ApplicationRunner {

    static final String DOMINIO = "@clinica-demo.test";
    static final int DURACION_TURNO_MIN = 30;
    static final int PROFESIONALES_POR_ESPECIALIDAD = 2;

    private static final List<String> NOMBRES = List.of(
            "Martín", "Lucía", "Alejandro", "Valeria", "Federico", "Carolina", "Sebastián", "Mariana",
            "Gustavo", "Florencia", "Diego", "Natalia", "Ricardo", "Paula", "Andrés", "Silvana",
            "Pablo", "Romina", "Matías", "Gabriela", "Hernán", "Laura", "Julián", "Cecilia");

    private static final List<String> APELLIDOS = List.of(
            "Fernández", "Gómez", "Rodríguez", "López", "Martínez", "Pérez", "Sánchez", "Romero", "Díaz",
            "Torres", "Álvarez", "Ruiz", "Ramírez", "Flores", "Benítez", "Acosta", "Medina", "Herrera",
            "Suárez", "Aguirre", "Peralta", "Molina", "Castro", "Ortiz", "Silva", "Rojas", "Navarro",
            "Ferreyra", "Cabrera", "Morales", "Vega", "Giménez", "Páez", "Quiroga", "Videla", "Godoy");

    /** Quien atiende la primera especialidad atiende también estas (lo que en una clínica real hace una misma persona). */
    private static final Map<String, List<String>> TAMBIEN_ATIENDEN = Map.of(
            "Ginecología", List.of("Obstetricia"),
            "Clínica Médica", List.of("Medicina Familiar"),
            "Endocrinología", List.of("Nutrición"),
            "Gastroenterología", List.of("Proctología"),
            "Traumatología y Ortopedia", List.of("Medicina del Deporte"),
            "Pediatría", List.of("Neonatología"),
            "Cirugía General", List.of("Cirugía Vascular"),
            "Neumonología", List.of("Alergia e Inmunología"));

    private final EspecialidadRepository especialidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final MedicoRepository medicoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final HorarioAtencionRepository horarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${clinica.seed.demo-medicos:false}")
    private boolean activado;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!activado) {
            return;
        }
        // Un solo hash para todas: nadie conoce la clave, así que no hace falta un BCrypt por cuenta.
        String claveDesconocida = passwordEncoder.encode(UUID.randomUUID().toString().replace("-", ""));
        LocalDate desde = LocalDate.now(clock).minusDays(30);
        List<Especialidad> catalogo = especialidadRepository.findAll().stream()
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre())).toList();
        Map<String, Especialidad> porNombre = new java.util.HashMap<>();
        catalogo.forEach(e -> porNombre.put(e.getNombre(), e));

        int creados = 0;
        int numero = 0;
        for (int i = 0; i < catalogo.size(); i++) {
            Especialidad principal = catalogo.get(i);
            // Quien ya atiende esta especialidad por haberla recibido de otra (ver TAMBIEN_ATIENDEN) cuenta: se completa hasta dos.
            int existentes = profesionalesDeDemostracion(principal);
            if (existentes >= PROFESIONALES_POR_ESPECIALIDAD) {
                continue;
            }
            Consultorio consultorio = consultorio(101 + i, principal.getNombre());
            for (int turno = existentes; turno < PROFESIONALES_POR_ESPECIALIDAD; turno++) {
                List<Especialidad> especialidades = new ArrayList<>(List.of(principal));
                if (turno == 0) {
                    for (String otra : TAMBIEN_ATIENDEN.getOrDefault(principal.getNombre(), List.of())) {
                        if (porNombre.containsKey(otra)) {
                            especialidades.add(porNombre.get(otra));
                        }
                    }
                }
                // El nombre y la matrícula salen del primer número libre, así que agregar una especialidad
                // al catálogo más adelante no pisa ni repite a nadie.
                while (!estaLibre(numero)) {
                    numero++;
                }
                crearMedico(numero++, especialidades, consultorio, turno == 0, desde, claveDesconocida);
                creados++;
            }
        }
        if (creados > 0) {
            log.warn("Se cargaron {} profesionales de DEMOSTRACIÓN (solo desarrollo local; sin contraseña conocida).", creados);
        }
    }

    private int profesionalesDeDemostracion(Especialidad especialidad) {
        return (int) medicoRepository.findByEspecialidades_Id(especialidad.getId()).stream()
                .filter(m -> m.getUsuario().getEmail().endsWith(DOMINIO)).count();
    }

    private boolean estaLibre(int numero) {
        return !usuarioRepository.existsByEmailIgnoreCase(email(numero))
                && medicoRepository.findByMatricula(matricula(numero)).isEmpty();
    }

    private static String email(int numero) {
        return ascii(NOMBRES.get(numero % NOMBRES.size())) + "." + ascii(APELLIDOS.get(numero % APELLIDOS.size())) + DOMINIO;
    }

    private static String matricula(int numero) {
        return String.format("DEMO-%04d", numero + 1);
    }

    private Consultorio consultorio(int numero, String especialidad) {
        String nombre = "Consultorio " + numero;
        if (consultorioRepository.existsByNombreIgnoreCase(nombre)) {
            return consultorioRepository.findAll().stream().filter(c -> c.getNombre().equalsIgnoreCase(nombre)).findFirst().orElseThrow();
        }
        return consultorioRepository.save(Consultorio.builder()
                .nombre(nombre).ubicacion("Sector " + especialidad).piso(numero / 100).build());
    }

    private void crearMedico(int numero, List<Especialidad> especialidades, Consultorio consultorio,
                             boolean aLaManana, LocalDate desde, String claveDesconocida) {
        String nombre = NOMBRES.get(numero % NOMBRES.size());
        String apellido = APELLIDOS.get(numero % APELLIDOS.size());
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email(email(numero)).passwordHash(claveDesconocida).rol(Rol.MEDICO).nombre(nombre).apellido(apellido).build());
        Medico medico = medicoRepository.save(Medico.builder()
                .usuario(usuario).matricula(matricula(numero))
                .especialidades(new ArrayList<>(especialidades)).build());

        List<DayOfWeek> dias = aLaManana
                ? List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        LocalTime inicio = aLaManana ? LocalTime.of(8, 0) : LocalTime.of(14, 0);
        for (DayOfWeek dia : dias) {
            horarioRepository.save(HorarioAtencion.builder()
                    .medico(medico).consultorio(consultorio).diaSemana(dia)
                    .horaInicio(inicio).horaFin(inicio.plusHours(4)).duracionTurnoMin(DURACION_TURNO_MIN)
                    .vigenteDesde(desde).build());
        }
    }

    /** "Martín Fernández" → "martin.fernandez": para el email, sin tildes ni la ñ. */
    private static String ascii(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
