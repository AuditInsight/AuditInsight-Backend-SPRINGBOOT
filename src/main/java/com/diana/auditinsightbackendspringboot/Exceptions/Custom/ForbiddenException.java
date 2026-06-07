package com.diana.auditinsightbackendspringboot.Exceptions.Custom;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
