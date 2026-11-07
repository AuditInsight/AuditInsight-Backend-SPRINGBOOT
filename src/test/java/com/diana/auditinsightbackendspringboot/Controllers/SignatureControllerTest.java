package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.Services.SignatureService;
import com.diana.auditinsightbackendspringboot.Services.DocumentConversionService;
import com.diana.auditinsightbackendspringboot.Services.SignedVersionService;
import com.diana.auditinsightbackendspringboot.Services.SigningModeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Calls the controller method directly: like the rest of this codebase's authenticated endpoints, download-signed
 * takes an {@code Authentication} argument that a standalone WebTestClient binding doesn't supply.
 */
@ExtendWith(MockitoExtension.class)
class SignatureControllerTest {

    @Mock private SignatureService signatureService;
    @Mock private SignedVersionService signedVersionService;
    @Mock private Authentication auth;

    @Test
    void downloadSigned_servesStoredVersionWithVersionAndHashHeaders() {
        UUID evidenceId = UUID.randomUUID();
        byte[] pdf = "stored-pdf".getBytes(StandardCharsets.UTF_8);
        String sha = "a".repeat(64);
        when(auth.getName()).thenReturn("signer@test.com");
        when(signedVersionService.serve(eq("signer@test.com"), eq(evidenceId), any())).thenReturn(Mono.just(
                new SignedVersionService.ServedVersion(UUID.randomUUID(), 3, sha, "contract-signed.pdf", pdf)));

        ResponseEntity<byte[]> response = new SignatureController(signatureService, signedVersionService,
                new SigningModeService(new DocumentConversionService("", 90)))
                .downloadSigned(auth, evidenceId, MockServerHttpRequest.get("/").build()).block();

        HttpHeaders headers = response.getHeaders();
        assertEquals(MediaType.APPLICATION_PDF, headers.getContentType());
        assertEquals("3", headers.getFirst(SignatureController.HEADER_VERSION));
        assertEquals(sha, headers.getFirst(SignatureController.HEADER_SHA256));
        assertEquals("\"" + sha + "\"", headers.getETag());
        assertTrue(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("contract-signed.pdf"));
        String exposed = headers.getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        assertTrue(exposed.contains(SignatureController.HEADER_VERSION) && exposed.contains(SignatureController.HEADER_SHA256));
        assertArrayEquals(pdf, response.getBody());
    }
}
