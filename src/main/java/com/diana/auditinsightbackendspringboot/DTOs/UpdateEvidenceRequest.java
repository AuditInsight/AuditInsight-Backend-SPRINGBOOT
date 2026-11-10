package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable evidence details. A null field is left unchanged; a blank note clears it. */
@Getter
@Setter
public class UpdateEvidenceRequest {
    @Size(max = 255, message = "Document name must be at most 255 characters")
    private String documentName;
    private String notes;
}
