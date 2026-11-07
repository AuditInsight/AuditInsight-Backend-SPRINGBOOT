package com.diana.auditinsightbackendspringboot.DTOs;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of checking a copy of a document against what was stored.
 * <ul>
 *   <li>ORIGINAL — byte-for-byte the original upload.</li>
 *   <li>SIGNED_VERSION — byte-for-byte stored signed version {@code versionNo}, signed by {@code signers}.</li>
 *   <li>NO_MATCH — neither: the file was changed, or it is a different document.</li>
 * </ul>
 */
public record VerificationResponse(String result, String sha256, Integer versionNo, Integer latestVersionNo,
                                   boolean latest, List<Signer> signers, String message) {

    public record Signer(String name, String title, LocalDateTime signedAt) {}
}
