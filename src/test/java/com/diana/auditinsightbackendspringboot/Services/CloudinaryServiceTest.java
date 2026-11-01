package com.diana.auditinsightbackendspringboot.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryServiceTest {

    private CloudinaryService service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryService();
        ReflectionTestUtils.setField(service, "cloudName", "test-cloud");
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiSecret", "test-secret");
        service.init();
    }

    @Test
    void signedDownloadUrlSignsPublicRawEvidenceUrl() throws Exception {
        String signedUrl = service.signedDownloadUrl(
                "https://res.cloudinary.com/test-cloud/raw/upload/v123/auditinsight/org/file.pdf");

        assertTrue(signedUrl.startsWith("https://res.cloudinary.com/test-cloud/raw/upload/s--"));
        assertTrue(signedUrl.contains("/v123/auditinsight/org/file.pdf"));
        assertFalse(signedUrl.contains("test-secret"));
    }

    @Test
    void signedDownloadUrlUsesTimeLimitedPrivateDownloadForAuthenticatedEvidence() throws Exception {
        String signedUrl = service.signedDownloadUrl(
                "https://res.cloudinary.com/test-cloud/raw/authenticated/v123/auditinsight/org/file.pdf");
        URI uri = URI.create(signedUrl);

        assertTrue(uri.getPath().endsWith("/raw/download"));
        assertTrue(uri.getQuery().contains("type=authenticated"));
        assertTrue(uri.getQuery().contains("public_id=auditinsight/org/file.pdf"));
        assertTrue(uri.getQuery().contains("expires_at="));
        assertFalse(uri.getQuery().contains("test-secret"));
    }

    @Test
    void signedDownloadUrlRejectsUntrustedHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> service.signedDownloadUrl("https://attacker.example/file.pdf"));
    }
}
