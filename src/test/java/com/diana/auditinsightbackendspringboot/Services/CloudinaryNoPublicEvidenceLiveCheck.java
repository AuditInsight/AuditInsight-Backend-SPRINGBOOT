package com.diana.auditinsightbackendspringboot.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Read-only live check, NOT part of the normal test run (enable with CLOUDINARY_LIVE_CHECK=true plus the
 * CLOUDINARY_* credentials): after the make-private job, no evidence file under auditinsight/ should still be
 * publicly delivered, and old public links to the now-private files should be refused. Changes nothing.
 */
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_LIVE_CHECK", matches = "true")
class CloudinaryNoPublicEvidenceLiveCheck {

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void noPublicEvidenceRemains_andOldPublicLinksAreRefused() throws Exception {
        CloudinaryService s = new CloudinaryService();
        ReflectionTestUtils.setField(s, "cloudName", System.getenv("CLOUDINARY_CLOUD_NAME"));
        ReflectionTestUtils.setField(s, "apiKey", System.getenv("CLOUDINARY_API_KEY"));
        ReflectionTestUtils.setField(s, "apiSecret", System.getenv("CLOUDINARY_API_SECRET"));
        s.init();
        Cloudinary cloudinary = (Cloudinary) ReflectionTestUtils.getField(s, "cloudinary");

        int publicCount = 0;
        List<String> privateUrls = new ArrayList<>();
        for (String resourceType : List.of("image", "raw")) {
            for (String type : List.of("upload", "authenticated")) {
                Map<?, ?> page = cloudinary.api().resources(ObjectUtils.asMap(
                        "resource_type", resourceType, "type", type, "prefix", "auditinsight/", "max_results", 500));
                List<?> resources = (List<?>) page.get("resources");
                System.out.println("[check] " + resourceType + "/" + type + ": " + resources.size() + " files");
                if ("upload".equals(type)) {
                    publicCount += resources.size();
                } else {
                    for (Object r : resources) {
                        privateUrls.add(String.valueOf(((Map<?, ?>) r).get("secure_url")));
                    }
                }
            }
        }

        // Old public form of a few switched files: same public id under /upload/ instead of /authenticated/.
        int checked = 0;
        for (String url : privateUrls) {
            if (checked == 6) break;
            String unsigned = CloudinaryService.withoutDeliverySignature(url);
            String oldPublic = unsigned.replace("/authenticated/", "/upload/");
            int privateStatus = status(unsigned);
            int oldStatus = status(oldPublic);
            System.out.println("[check] " + (url.contains("/image/") ? "image" : "raw  ")
                    + " private link -> HTTP " + privateStatus + ", old public link -> HTTP " + oldStatus);
            assertTrue(privateStatus != 200 && oldStatus != 200, "no link may open the file without the API");
            checked++;
        }
        if (System.getenv("LIST_PUBLIC") != null) {
            for (String resourceType : List.of("image", "raw")) {
                Map<?, ?> page = cloudinary.api().resources(ObjectUtils.asMap(
                        "resource_type", resourceType, "type", "upload", "prefix", "auditinsight/", "max_results", 500));
                for (Object o : (List<?>) page.get("resources")) {
                    Map<?, ?> r = (Map<?, ?>) o;
                    String id = String.valueOf(r.get("public_id"));
                    String[] parts = id.split("/");
                    System.out.println("[public] folder=" + (parts.length > 2 ? parts[1] : "-") + " created=" + r.get("created_at")
                            + " format=" + r.get("format") + " bytes=" + r.get("bytes"));
                }
            }
        }
        assertEquals(0, publicCount, "no evidence file may remain publicly delivered");
    }

    private int status(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
