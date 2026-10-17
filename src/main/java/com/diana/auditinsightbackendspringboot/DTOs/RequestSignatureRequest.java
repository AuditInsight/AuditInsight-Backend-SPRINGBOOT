package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RequestSignatureRequest {
    // Members picked from the organisation list. At least one of userIds / emails is required.
    private List<Long> userIds;

    // Members identified by their login email (must belong to the same organisation).
    private List<@Email(message = "provide valid emails") String> emails;

    @Size(max = 1000, message = "message is too long")
    private String message;
}
