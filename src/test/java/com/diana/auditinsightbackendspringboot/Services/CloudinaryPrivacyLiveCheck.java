package com.diana.auditinsightbackendspringboot.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live check against the real Cloudinary account — NOT part of the normal test run. Enable with
 * CLOUDINARY_LIVE_CHECK=true plus the CLOUDINARY_* credentials. Uploads two tiny throwaway files to
 * auditinsight/storage-check, and verifies that:
 * <ol>
 *   <li>a private ("authenticated") upload can't be opened by its plain URL, but the backend can still download it
 *       byte for byte;</li>
 *   <li>an existing public file can be switched to private, keeps its bytes, and is still downloadable;</li>
 * </ol>
 * for both a raw file (PDF) and an image (PNG). Everything it uploads is deleted afterwards.
 */
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_LIVE_CHECK", matches = "true")
class CloudinaryPrivacyLiveCheck {

    private final HttpClient http = HttpClient.newHttpClient();

    private static CloudinaryService service(String deliveryType) {
        CloudinaryService s = new CloudinaryService();
        ReflectionTestUtils.setField(s, "cloudName", System.getenv("CLOUDINARY_CLOUD_NAME"));
        ReflectionTestUtils.setField(s, "apiKey", System.getenv("CLOUDINARY_API_KEY"));
        ReflectionTestUtils.setField(s, "apiSecret", System.getenv("CLOUDINARY_API_SECRET"));
        ReflectionTestUtils.setField(s, "deliveryType", deliveryType);
        s.init();
        return s;
    }

    /** Removes everything under auditinsight/storage-check (all resource and delivery types), including leftovers. */
    @AfterAll
    static void cleanUpStorageCheckFolder() {
        Cloudinary cloudinary = (Cloudinary) ReflectionTestUtils.getField(service("upload"), "cloudinary");
        for (String resourceType : List.of("image", "raw")) {
            for (String type : List.of("upload", "authenticated")) {
                try {
                    Map<?, ?> result = cloudinary.api().deleteResourcesByPrefix("auditinsight/storage-check/",
                            ObjectUtils.asMap("resource_type", resourceType, "type", type));
                    System.out.println("[cleanup] " + resourceType + "/" + type + ": " + result.get("deleted"));
                } catch (Exception e) {
                    System.out.println("[cleanup] " + resourceType + "/" + type + " failed: " + e.getMessage());
                }
            }
        }
    }

    private int plainGetStatus(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    @Test
    void pdf_privateUploadAndSwitchToPrivate() throws Exception {
        check("%PDF-1.4\n% AuditInsight storage check\n".getBytes(StandardCharsets.US_ASCII), "pdf");
    }

    @Test
    void png_privateUploadAndSwitchToPrivate() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        check(out.toByteArray(), "png");
    }

    private void check(byte[] bytes, String fileType) throws Exception {
        CloudinaryService privateStore = service("authenticated");
        CloudinaryService publicStore = service("upload");
        String privateUrl = null;
        String publicUrl = null;
        String movedUrl = null;
        try {
            // 1. New private upload.
            privateUrl = privateStore.upload(bytes, "storage-check", fileType).block();
            System.out.println("[" + fileType + "] private upload: " + privateUrl);
            assertTrue(CloudinaryService.isPrivateUrl(privateUrl), "upload should be private");
            int status = plainGetStatus(privateUrl);
            System.out.println("[" + fileType + "] plain GET of private URL -> HTTP " + status);
            assertNotEquals(200, status, "a private file must not open from its plain URL");
            assertArrayEquals(bytes, privateStore.downloadOriginal(privateUrl).block(),
                    "the backend must still download the private file byte for byte");

            // 2. Existing public file switched to private.
            publicUrl = publicStore.upload(bytes, "storage-check", fileType).block();
            System.out.println("[" + fileType + "] public upload: " + publicUrl + " -> HTTP " + plainGetStatus(publicUrl));
            movedUrl = publicStore.makePrivate(publicUrl).block();
            System.out.println("[" + fileType + "] switched to private: " + movedUrl);
            assertTrue(CloudinaryService.isPrivateUrl(movedUrl));
            assertArrayEquals(bytes, publicStore.downloadOriginal(movedUrl).block(),
                    "a switched file must keep its bytes and stay downloadable by the backend");
            System.out.println("[" + fileType + "] plain GET of switched URL -> HTTP " + plainGetStatus(movedUrl));
            System.out.println("[" + fileType + "] plain GET of old public URL -> HTTP " + plainGetStatus(publicUrl)
                    + " (CDN cache invalidation can take a few minutes)");
        } finally {
            for (String url : new String[]{privateUrl, movedUrl != null ? movedUrl : publicUrl}) {
                if (url != null) {
                    try {
                        privateStore.delete(url).block();
                        System.out.println("[" + fileType + "] deleted " + url);
                    } catch (Exception e) {
                        System.out.println("[" + fileType + "] could not delete " + url + ": " + e.getMessage());
                    }
                }
            }
        }
    }
}
