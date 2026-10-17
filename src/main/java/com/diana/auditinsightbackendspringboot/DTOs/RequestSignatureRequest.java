package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RequestSignatureRequest {
    @NotEmpty(message = "select at least one member")
    private List<Long> userIds;

    @Size(max = 1000, message = "message is too long")
    private String message;
}
