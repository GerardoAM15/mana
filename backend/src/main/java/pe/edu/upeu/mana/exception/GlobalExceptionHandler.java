package pe.edu.upeu.mana.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import pe.edu.upeu.mana.exception.dto.ErrorResponseDTO;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public GlobalExceptionHandler(java.time.Clock clock) {
        this.clock = clock;
    }

    private final java.time.Clock clock;

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponseDTO> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var campos = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
        log.warn("Validacion rechazada | ruta={} | campos={}", req.getRequestURI(), campos.keySet());
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(ahora(), 400, "Bad Request", "Datos invalidos", req.getRequestURI(), campos));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ErrorResponseDTO> noEncontrado(RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponseDTO> rutaNoEncontrada(NoResourceFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "Ruta no encontrada", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponseDTO> metodoNoPermitido(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Metodo HTTP no permitido", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponseDTO> contenidoNoSoportado(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado; utilice application/json", req);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ErrorResponseDTO> respuestaNoDisponible(HttpMediaTypeNotAcceptableException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_ACCEPTABLE, "Formato de respuesta no disponible; utilice application/json", req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ErrorResponseDTO> conflicto(RuntimeException ex, HttpServletRequest req) {
        log.warn("Regla rechazada | ruta={} | motivo={}", req.getRequestURI(), ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(DatosInvalidosException.class)
    ResponseEntity<ErrorResponseDTO> invalido(DatosInvalidosException ex, HttpServletRequest req) {
        return solicitudInvalida(ex.getMessage(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponseDTO> cuerpoInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        for (Throwable causa = ex.getCause(); causa != null; causa = causa.getCause()) {
            if (causa instanceof MismatchedInputException formato) {
                var campo = new StringBuilder();
                for (var referencia : formato.getPath()) {
                    if (referencia.getPropertyName() != null) {
                        if (!campo.isEmpty()) {
                            campo.append('.');
                        }
                        campo.append(referencia.getPropertyName());
                    } else if (referencia.getIndex() >= 0) {
                        campo.append('[').append(referencia.getIndex()).append(']');
                    }
                }
                String nombre = campo.isEmpty() ? "El cuerpo de la solicitud" : "El campo '" + campo + "'";
                return solicitudInvalida(mensajeTipo(nombre, formato.getTargetType()), req);
            }
        }
        return solicitudInvalida("Cuerpo JSON inválido: revise la sintaxis y envíe un objeto JSON válido", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponseDTO> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return solicitudInvalida(mensajeTipo("El parámetro '" + ex.getName() + "'", ex.getRequiredType()), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponseDTO> parametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return solicitudInvalida("Falta el parámetro obligatorio '" + ex.getParameterName() + "'", req);
    }

    private String mensajeTipo(String nombre, Class<?> tipo) {
        if (tipo == Long.class || tipo == long.class || tipo == Integer.class || tipo == int.class
                || tipo == Short.class || tipo == short.class || tipo == Byte.class || tipo == byte.class) {
            return nombre + " debe ser un número entero, sin decimales y dentro del rango permitido";
        }
        if (tipo == LocalDate.class) {
            return nombre + " debe ser una fecha válida con formato yyyy-MM-dd";
        }
        return nombre + " tiene un tipo o valor inválido";
    }

    private ResponseEntity<ErrorResponseDTO> solicitudInvalida(String mensaje, HttpServletRequest req) {
        log.warn("Solicitud rechazada | ruta={} | motivo={}", req.getRequestURI(), mensaje);
        return error(HttpStatus.BAD_REQUEST, mensaje, req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponseDTO> integridad(Exception ex, HttpServletRequest req) {
        log.warn("Conflicto de integridad o concurrencia | ruta={}", req.getRequestURI());
        return error(HttpStatus.CONFLICT, "Conflicto: dato duplicado, recurso relacionado o en uso; vuelva a consultar", req);
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, org.springframework.dao.OptimisticLockingFailureException.class})
    ResponseEntity<ErrorResponseDTO> concurrencia(Exception ex,HttpServletRequest req) {
        return error(HttpStatus.CONFLICT,"Otro usuario modificó el registro; vuelva a consultar",req);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponseDTO> autenticacion(AuthenticationException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Credenciales invalidas", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponseDTO> denegado(AccessDeniedException ex, HttpServletRequest req) {
        String mensaje = "No tiene permiso para gestionar este usuario".equals(ex.getMessage())
                ? ex.getMessage() : "No tiene permiso para esta operacion";
        return error(HttpStatus.FORBIDDEN, mensaje, req);
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    ResponseEntity<ErrorResponseDTO> limite(DemasiadosIntentosException ex, HttpServletRequest req) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req);
    }

    @ExceptionHandler(ServicioExternoException.class)
    ResponseEntity<ErrorResponseDTO> servicioExterno(ServicioExternoException ex, HttpServletRequest req) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponseDTO> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado | ruta={}", req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req);
    }

    private ResponseEntity<ErrorResponseDTO> error(HttpStatus status, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ErrorResponseDTO(ahora(), status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI(), null));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }
}
