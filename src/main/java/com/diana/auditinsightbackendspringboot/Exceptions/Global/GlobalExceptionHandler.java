package com.diana.auditinsightbackendspringboot.Exceptions.Global;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleForbidden(ForbiddenException ex) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "failed", "message", ex.getMessage(), "code", 403)));
    }

    @ExceptionHandler(InvalidRecord.class)
    public Mono<ResponseEntity<Map<String, String>>> handleInvalidRecord(InvalidRecord ex, ServerWebExchange exchange) {
        log.warn("400 on {} {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleIllegalState(IllegalStateException ex, ServerWebExchange exchange) {
        log.warn("400 on {} {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage())));
    }

    // A DB-level constraint rejected the write (e.g. a NOT NULL/FK/unique violation) —
    // a client-fixable 400, not a server fault, so it shouldn't surface as a 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleDataIntegrityViolation(DataIntegrityViolationException ex, ServerWebExchange exchange) {
        log.warn("400 (data integrity violation) on {} {}: {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "The request conflicts with existing data or violates a required constraint.")));
    }

    // Handles @Valid bean validation failures in WebFlux
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleValidation(WebExchangeBindException ex, ServerWebExchange exchange) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("400 (validation) on {} {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), errors);
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", errors)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, String>>> handleGeneric(Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled exception [{}] on {} {}: {}",
                ex.getClass().getSimpleName(), exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.getMessage(), ex);
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "An unexpected error occurred.";
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", message)));
    }
}

