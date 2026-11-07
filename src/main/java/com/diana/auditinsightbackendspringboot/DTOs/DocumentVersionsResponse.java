package com.diana.auditinsightbackendspringboot.DTOs;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A document's history: version 0 is the original upload; versions 1..n are the stored signed copies, each
 * holding the first {@code signatureCount} signatures in signing order.
 */
public record DocumentVersionsResponse(Original original, List<Version> versions) {

    /** {@code sha256} is null until the original's fingerprint has been recorded (older uploads). */
    public record Original(String documentName, String fileType, String sha256, LocalDateTime uploadedAt) {}

    public record Version(int versionNo, String sha256, long sizeBytes, int signatureCount, LocalDateTime createdAt) {}
}
