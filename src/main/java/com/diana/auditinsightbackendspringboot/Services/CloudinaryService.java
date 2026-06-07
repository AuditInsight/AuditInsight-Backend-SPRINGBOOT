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

@Service
@Slf4j
public class CloudinaryService {

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



    public Mono<String> upload(byte[] fileBytes, String folder) {
        return Mono.fromCallable(() -> {
            try {
                Map<?, ?> result = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                        "folder", "auditinsight/" + folder,
                        "resource_type", "auto"
                ));
                return (String) result.get("secure_url");
            } catch (Exception e) {
                log.error("Cloudinary upload failed for folder '{}': {}", folder, e.getMessage(), e);
                throw new RuntimeException("File upload failed: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}