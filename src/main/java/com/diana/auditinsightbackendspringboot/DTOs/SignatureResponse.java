package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SignatureResponse {
    private UUID id;
    private UUID evidenceId;
    private Long signerId;
    private String signerName;
    private String typedName;
    private String signatureImage;
    private LocalDateTime signedAt;
    // true when someone asked this person to sign; false when they signed on their own initiative
    private boolean requested;
}
