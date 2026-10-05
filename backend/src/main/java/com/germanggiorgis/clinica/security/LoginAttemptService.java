package com.germanggiorgis.clinica.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos contra la fuerza bruta, en memoria (una sola instancia del backend):
 * <ul>
 *   <li>Login: tras N fallos para el mismo email dentro de la ventana, ese email queda bloqueado
 *       hasta que el fallo más viejo salga de la ventana, aunque llegue la contraseña correcta.
 *       Además se limitan los fallos por IP (que cubre al que prueba muchos emails distintos).</li>
 *   <li>Registro: un máximo de altas por IP y por hora, para frenar el alta masiva de cuentas y la
 *       enumeración de emails existentes.</li>
 * </ul>
 * Un éxito de login borra los fallos de ese email. Con varias instancias detrás de un balanceador
 * habría que mover esto a un almacén compartido (Redis, la base).
 *
 * <p>La IP es la de la conexión ({@code getRemoteAddr}). Detrás de un proxy inverso hay que configurar
 * {@code server.forward-headers-strategy}, o todos los usuarios parecerían venir de la misma IP.
 */
@Component
public class LoginAttemptService {

    private static final Duration VENTANA_REGISTRO = Duration.ofHours(1);

    private final Clock clock;
    private final int maxFallosPorEmail;
    private final int maxFallosPorIp;
    private final int maxRegistrosPorIp;
    private final Duration ventana;
    private final int maxClaves;

    private final Map<String, Deque<Instant>> fallosPorEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> fallosPorIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> registrosPorIp = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(
            Clock clock,
            @Value("${clinica.seguridad.login-max-fallos-por-email:5}") int maxFallosPorEmail,
            @Value("${clinica.seguridad.login-max-fallos-por-ip:30}") int maxFallosPorIp,
            @Value("${clinica.seguridad.registro-max-por-ip-por-hora:10}") int maxRegistrosPorIp,
            @Value("${clinica.seguridad.ventana-minutos:15}") int ventanaMinutos,
            @Value("${clinica.seguridad.max-claves-en-memoria:10000}") int maxClaves
    ) {
        this.clock = clock;
        this.maxFallosPorEmail = maxFallosPorEmail;
        this.maxFallosPorIp = maxFallosPorIp;
        this.maxRegistrosPorIp = maxRegistrosPorIp;
        this.ventana = Duration.ofMinutes(ventanaMinutos);
        this.maxClaves = maxClaves;
    }

    /** Lanza 429 si ese email o esa IP ya agotaron sus intentos. Va ANTES de mirar la contraseña. */
    public void verificarLoginPermitido(String email, String ip) {
        exigirSinBloqueo(fallosPorEmail.get(normalizar(email)), maxFallosPorEmail, ventana);
        exigirSinBloqueo(fallosPorIp.get(ip), maxFallosPorIp, ventana);
    }

    public void registrarFallo(String email, String ip) {
        anotar(fallosPorEmail, normalizar(email), ventana);
        anotar(fallosPorIp, ip, ventana);
    }

    public void registrarExito(String email) {
        fallosPorEmail.remove(normalizar(email));
    }

    /** Cuenta este intento de registro y lanza 429 si esa IP ya hizo demasiados en la última hora. */
    public void verificarRegistroPermitido(String ip) {
        exigirSinBloqueo(registrosPorIp.get(ip), maxRegistrosPorIp, VENTANA_REGISTRO);
        anotar(registrosPorIp, ip, VENTANA_REGISTRO);
    }

    /** Cantidad de claves guardadas en memoria; para comprobar en los tests que está acotada. */
    int clavesEnMemoria() {
        return fallosPorEmail.size() + fallosPorIp.size() + registrosPorIp.size();
    }

    private void exigirSinBloqueo(Deque<Instant> intentos, int maximo, Duration ventanaDeEsteLimite) {
        if (intentos == null) {
            return;
        }
        Instant ahora = clock.instant();
        synchronized (intentos) {
            purgarVencidos(intentos, ahora, ventanaDeEsteLimite);
            if (intentos.size() >= maximo) {
                Instant liberaEn = intentos.peekFirst().plus(ventanaDeEsteLimite);
                long segundos = Math.max(1, Duration.between(ahora, liberaEn).toSeconds());
                throw new DemasiadosIntentosException(segundos);
            }
        }
    }

    private void anotar(Map<String, Deque<Instant>> mapa, String clave, Duration ventanaDeEsteLimite) {
        Instant ahora = clock.instant();
        if (mapa.size() >= maxClaves && !mapa.containsKey(clave)) {
            liberarEspacio(mapa, ahora, ventanaDeEsteLimite);
        }
        Deque<Instant> intentos = mapa.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (intentos) {
            purgarVencidos(intentos, ahora, ventanaDeEsteLimite);
            intentos.addLast(ahora);
        }
    }

    private void purgarVencidos(Deque<Instant> intentos, Instant ahora, Duration ventanaDeEsteLimite) {
        Instant limite = ahora.minus(ventanaDeEsteLimite);
        while (!intentos.isEmpty() && !intentos.peekFirst().isAfter(limite)) {
            intentos.pollFirst();
        }
    }

    /**
     * El mapa no puede crecer sin límite si alguien manda fallos con miles de emails distintos:
     * primero se tiran las claves ya vencidas y, si no alcanza, se descartan algunas.
     */
    private void liberarEspacio(Map<String, Deque<Instant>> mapa, Instant ahora, Duration ventanaDeEsteLimite) {
        Instant limite = ahora.minus(ventanaDeEsteLimite);
        mapa.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || !e.getValue().peekLast().isAfter(limite);
            }
        });
        Iterator<String> it = mapa.keySet().iterator();
        while (mapa.size() >= maxClaves && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
