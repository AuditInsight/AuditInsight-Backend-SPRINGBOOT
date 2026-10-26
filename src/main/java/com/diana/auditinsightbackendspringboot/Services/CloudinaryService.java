package com.diana.auditinsightbackendspringboot.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class CloudinaryService {

    private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "jpeg");

    private Cloudinary cloudinary;
    @Value("${CLOUDINARY_CLOUD_NAME}")
    private String cloudName;
    @Value("${CLOUDINARY_API_KEY}")
    private String apiKey;
    @Value("${CLOUDINARY_API_SECRET}")
    private String apiSecret;

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
     *                 which otherwise makes every stored document 401 on download.
     */
    public Mono<String> upload(byte[] fileBytes, String folder, String fileType) {
        String resourceType = IMAGE_TYPES.contains(fileType == null ? "" : fileType.toLowerCase()) ? "image" : "raw";
        return Mono.fromCallable(() -> {
            try {
                Map<?, ?> result = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                        "folder", "auditinsight/" + folder,
                        "resource_type", resourceType
                ));
                return (String) result.get("secure_url");
            } catch (Exception e) {
                log.error("Cloudinary upload failed for folder '{}': {}", folder, e.getMessage(), e);
                throw new RuntimeException("File upload failed: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}