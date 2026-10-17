package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignEvidenceRequest {
    // Optional: the full names the signer wants printed with the signature. Defaults to the account name.
    @Size(max = 255, message = "fullName is too long")
    private String fullName;

    // Optional: the signer's job title (e.g. Finance Manager).
    @Size(max = 255, message = "title is too long")
    private String title;

    // Optional: the name the signer typed. The account name is always recorded regardless.
    @Size(max = 255, message = "typedName is too long")
    private String typedName;

    // Optional: drawn signature as a PNG data URL.
    @Size(max = 500_000, message = "signatureImage is too large")
    private String signatureImage;
}
