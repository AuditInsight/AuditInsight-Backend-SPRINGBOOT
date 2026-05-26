package com.diana.auditinsightbackendspringboot.Exceptions.Global;



import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice

public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRecord.class)
    public ResponseEntity<Map<String,Object>> handleWrongCredentials(InvalidRecord ex) {
        Map<String,Object> map = new HashMap<>();
        map.put("message", ex.getMessage());
        map.put("status" , HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(map , HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleException(MethodArgumentNotValidException ex) {
        Map<String,Object> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField() , error.getDefaultMessage());
        });

        return new ResponseEntity<>(errors , HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unauthorized")) {
            status = HttpStatus.FORBIDDEN;
        }

        return buildErrorResponse(ex.getMessage(), status);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse("Access Denied: You do not have permission to access this resource.", HttpStatus.FORBIDDEN);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {

        return buildErrorResponse("An unexpected internal server error occurred: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", status.value());
        map.put("message", message);
        return new ResponseEntity<>(map, status);
    }
}

