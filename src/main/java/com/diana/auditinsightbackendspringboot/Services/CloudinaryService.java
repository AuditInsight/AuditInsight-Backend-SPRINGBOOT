package com.diana.auditinsightbackendspringboot.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CloudinaryService {

    private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "jpeg");
    private static final Set<String> RESOURCE_TYPES = Set.of("image", "raw", "video");
    private static final Set<String> DELIVERY_TYPES = Set.of("upload", "private", "authenticated");
    private static final int MAX_DOWNLOAD_BYTES = 30 * 1024 * 1024;

    private Cloudinary cloudinary;
    @Value("${CLOUDINARY_CLOUD_NAME}")
    private String cloudName;
    @Value("${CLOUDINARY_API_KEY}")
    private String apiKey;
    @Value("${CLOUDINARY_API_SECRET}")
    private String apiSecret;
    private final WebClient webClient = WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_DOWNLOAD_BYTES))
                    .build())
            .build();

    @PostConstruct
    public void init() {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
        log.info("Cloudinary initialized for cloud: {}", cloudName);
    }



    /**
     * @param fileType the file's extension (pdf, xlsx, png, ...), used to pick the Cloudinary
     *                 resource type. Non-image documents are uploaded as "raw" rather than "auto"
     *                 (which Cloudinary resolves to "image" for PDFs) — Cloudinary blocks public
     *                 delivery of PDF/ZIP files served through the image/video pipeline by default,
     *                 which otherwise makes every stored document 401 on download. Delivery type and
     *                 access mode are also forced to public/upload explicitly: this account's default
     *                 for non-image uploads is "authenticated", which otherwise denies delivery of
     *                 every raw file with a "deny or ACL failure" error even once resource_type is
     *                 correct.
     */
    public Mono<String> upload(byte[] fileBytes, String folder, String fileType) {
        String resourceType = IMAGE_TYPES.contains(fileType == null ? "" : fileType.toLowerCase()) ? "image" : "raw";
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> options = new HashMap<>();
                options.put("folder", "auditinsight/" + folder);
                options.put("resource_type", resourceType);
                options.put("type", "upload");
                options.put("access_mode", "public");
                if ("raw".equals(resourceType)) {
                    // Unlike "image", Cloudinary never appends a format/extension to a "raw"
                    // public ID on its own, so without one the stored URL has no extension and
                    // browsers can't infer the right Content-Type to preview or download it.
                    options.put("public_id", UUID.randomUUID() + "." + fileType);
                }
                Map<?, ?> result = cloudinary.uploader().upload(fileBytes, options);
                return (String) result.get("secure_url");
            } catch (Exception e) {
                log.error("Cloudinary upload failed for folder '{}': {}", folder, e.getMessage(), e);
                throw new RuntimeException("File upload failed: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<byte[]> downloadOriginal(String fileUrl) {
        return Mono.fromCallable(() -> signedDownloadUrl(fileUrl))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(url -> webClient.get().uri(url).retrieve().bodyToMono(byte[].class));
    }

    String signedDownloadUrl(String fileUrl) throws Exception {
        URI uri = URI.create(fileUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Evidence file URL is not a trusted Cloudinary URL");
        }

        String[] segments = uri.getPath().split("/");
        if (segments.length < 7
                || !cloudName.equals(segments[1])
                || !RESOURCE_TYPES.contains(segments[2])
                || !DELIVERY_TYPES.contains(segments[3])
                || !segments[4].matches("v\\d+")) {
            throw new IllegalArgumentException("Evidence file URL has an unsupported Cloudinary path");
        }

        String resourceType = segments[2];
        String deliveryType = segments[3];
        String version = segments[4].substring(1);
        String publicId = String.join("/", Arrays.copyOfRange(segments, 5, segments.length));
        String format = null;
        if ("image".equals(resourceType)) {
            int extension = publicId.lastIndexOf('.');
            if (extension <= 0 || extension == publicId.length() - 1) {
                throw new IllegalArgumentException("Image evidence URL is missing its format");
            }
            format = publicId.substring(extension + 1);
            publicId = publicId.substring(0, extension);
        }

        if (!"upload".equals(deliveryType)) {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", resourceType);
            options.put("type", deliveryType);
            options.put("expires_at", Instant.now().plusSeconds(300).getEpochSecond());
            return cloudinary.privateDownload(publicId, format, options);
        }

        var url = cloudinary.url()
                .resourceType(resourceType)
                .type(deliveryType)
                .publicId(publicId)
                .version(version)
                .secure(true)
                .signed(true);
        if (format != null) {
            url.format(format);
        }
        return url.generate();
    }
}