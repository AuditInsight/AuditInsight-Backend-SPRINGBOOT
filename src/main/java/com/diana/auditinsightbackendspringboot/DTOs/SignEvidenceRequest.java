package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    // Optional: where to stamp the signature on the document (PDF and image documents only). When empty, the
    // signature goes into a "Signatures" block after the content, as before.
    @Valid
    @Size(max = 20, message = "a signature can be placed at most 20 times")
    private List<SignaturePlacementRequest> placements;

    // Optional: SHA-256 of the document the signer placed the signature on (from GET signing-base), kept as an
    // audit record of exactly what they saw.
    @Pattern(regexp = "^[0-9a-f]{64}$", message = "baseSha256 must be a lowercase hex SHA-256")
    private String baseSha256;
}
