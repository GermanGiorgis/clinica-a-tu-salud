package com.germanggiorgis.clinica.controller;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo error sale con la misma forma, {"error": "..."} (y "errores" por campo en
 * las validaciones), y nunca con el detalle interno: ni el texto de una
 * excepción de la base ni un stack trace llegan al cliente.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales inválidas"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "No tenés permiso para hacer esto"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .headers(ex.getHeaders())   // p. ej. Retry-After en un 429
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Error"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // Un campo vacío incumple a la vez "es obligatorio" y su formato; de las dos, la persona necesita leer la primera.
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(fieldError -> !esDeObligatoriedad(fieldError.getCode())))
                .forEach(fieldError -> errores.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Revisá los datos ingresados", "errores", errores));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Revisá los datos ingresados"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "El cuerpo de la solicitud no es válido"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "El valor del parámetro \"" + ex.getName() + "\" no es válido"));
    }

    /**
     * Violación de una restricción de la base que ningún servicio atajó antes
     * (borrar algo que está en uso, dos pedidos simultáneos que chocan contra un
     * índice único, etc.): es un conflicto con el estado actual, no un error del servidor.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Restricción de la base violada: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "La operación entra en conflicto con otros datos (por ejemplo, un registro que ya existe o que está en uso)"));
    }

    /** Dos personas modificaron el mismo registro a la vez; gana la primera que guardó. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Alguien modificó este registro al mismo tiempo. Recargá y volvé a intentar"));
    }

    /**
     * Otros choques entre operaciones simultáneas: sobre todo, un deadlock que Postgres detecta entre
     * dos reservas que se pisan (una de las dos transacciones se cancela, y es seguro reintentar).
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, String>> handleConcurrencia(ConcurrencyFailureException ex) {
        log.warn("Conflicto de concurrencia: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Otra operación simultánea chocó con esta. Probá de nuevo"));
    }

    /**
     * Red de seguridad. Los errores HTTP que Spring ya modela (ruta inexistente,
     * método no permitido, falta un parámetro) conservan su código; cualquier otra
     * cosa es un 500 con mensaje genérico, y el detalle va solo al log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOtros(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status).body(Map.of("error", mensajePara(status)));
        }
        log.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Ocurrió un error inesperado. Probá de nuevo en unos minutos"));
    }

    private static boolean esDeObligatoriedad(String codigo) {
        return "NotBlank".equals(codigo) || "NotNull".equals(codigo) || "NotEmpty".equals(codigo);
    }

    private static String mensajePara(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Solicitud inválida";
            case 404 -> "No se encontró lo que buscás";
            case 405 -> "Ese método no está permitido en esta ruta";
            case 415 -> "Tipo de contenido no soportado";
            default -> "No se pudo procesar la solicitud";
        };
    }
}
