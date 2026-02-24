package com.arteva.medbot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Централизованный обработчик исключений для REST-контроллеров.
 * <p>
 * Обеспечивает единообразный формат ошибок во всех эндпоинтах.
 * Обрабатываемые исключения:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} → 400 (ошибка валидации)</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 (невалидный JSON)</li>
 *   <li>{@link HttpRequestMethodNotSupportedException} → 405 (неверный HTTP-метод)</li>
 *   <li>{@link RuntimeException} / {@link Exception} → 500 (без утечки деталей)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Обработка ошибок валидации (Jakarta Validation).
     *
     * @param ex исключение с ошибками полей
     * @return 400 Bad Request со списком ошибок
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Validation failed",
                        "details", errors
                ));
    }

    /**
     * Обработка невалидного JSON в теле запроса.
     *
     * @param ex исключение десериализации
     * @return 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "Malformed request body",
                        "message", "Request body is missing or not valid JSON"
                ));
    }

    /**
     * Обработка неподдерживаемого HTTP-метода.
     *
     * @param ex исключение
     * @return 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of(
                        "error", "Method not allowed",
                        "message", ex.getMessage() != null ? ex.getMessage() : "HTTP method not supported"
                ));
    }

    /**
     * Обработка неожиданных исключений времени выполнения.
     * <p>
     * Не раскрывает детали ошибки клиенту (безопасность).
     *
     * @param ex исключение
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled runtime exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal server error",
                        "message", "An unexpected error occurred"
                ));
    }

    /**
     * Обработка любых неперехваченных исключений (страховочная сеть).
     *
     * @param ex исключение
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal server error",
                        "message", "An unexpected error occurred"
                ));
    }
}
