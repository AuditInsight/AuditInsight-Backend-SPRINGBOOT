package com.diana.auditinsightbackendspringboot.Services;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
    void authenticatedSignedDownloadUrlSupportsLegacyAssetsWithRestrictedPublicDelivery() throws Exception {
        String signedUrl = service.authenticatedSignedDownloadUrl(
                "https://res.cloudinary.com/test-cloud/raw/upload/v123/auditinsight/org/file.pdf");

        assertTrue(signedUrl.startsWith("https://res.cloudinary.com/test-cloud/raw/authenticated/s--"));
        assertTrue(signedUrl.contains("/v123/auditinsight/org/file.pdf"));
        assertFalse(signedUrl.contains("test-secret"));
    }

    @Test
    void assetIdDownloadUrlSignsShortLivedCloudinaryApiRequest() {
        URI uri = URI.create(service.buildAssetIdDownloadUrl("asset-id-123"));
        Map<String, String> query = new HashMap<>();
        for (String part : uri.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            query.put(pair[0], pair[1]);
        }

        assertTrue(uri.getPath().equals("/v1_1/test-cloud/asset/download"));
        assertTrue(query.get("asset_id").equals("asset-id-123"));
        assertTrue(query.get("attachment").equals("true"));
        assertTrue(query.get("api_key").equals("test-key"));
        assertTrue(Long.parseLong(query.get("expires_at")) - Long.parseLong(query.get("timestamp")) == 300);
        assertFalse(query.get("signature").contains("test-secret"));

        Map<String, Object> signedParameters = Map.of(
                "asset_id", query.get("asset_id"),
                "attachment", query.get("attachment"),
                "expires_at", Long.parseLong(query.get("expires_at")),
                "timestamp", Long.parseLong(query.get("timestamp")));
        Cloudinary cloudinary = (Cloudinary) ReflectionTestUtils.getField(service, "cloudinary");
        assertTrue(query.get("signature").equals(cloudinary.apiSignRequest(signedParameters, "test-secret")));
    }

    @Test
    void signedDownloadUrlRejectsUntrustedHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> service.signedDownloadUrl("https://attacker.example/file.pdf"));
    }
}
