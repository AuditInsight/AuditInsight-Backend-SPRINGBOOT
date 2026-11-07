package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * Converts Word/Excel documents to PDF with Gotenberg (a LibreOffice-based conversion service running as its own
 * Railway service), so they can be signed on the page like a PDF. Gotenberg only converts: nothing is stored there;
 * the resulting PDF is stored in Cloudinary like every other file.
 * <p>
 * Disabled when {@code gotenberg.url} is empty — Word/Excel documents then keep the signature certificate.
 */
@Service
@Slf4j
public class DocumentConversionService {

    private static final Set<String> OFFICE_TYPES = Set.of("docx", "xlsx", "xls");
    private static final int MAX_PDF_BYTES = 50 * 1024 * 1024;

    private final String baseUrl;
    private final Duration timeout;
    private final WebClient webClient;

    public DocumentConversionService(@Value("${gotenberg.url:}") String baseUrl,
                                     @Value("${gotenberg.timeout-seconds:90}") long timeoutSeconds) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.webClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_PDF_BYTES))
                        .build())
                .build();
    }

    public static boolean isOfficeType(String fileType) {
        return fileType != null && OFFICE_TYPES.contains(fileType.toLowerCase());
    }

    /** True when a converter is configured, i.e. Word/Excel files can be signed on the page. */
    public boolean isEnabled() {
        return !baseUrl.isEmpty();
    }

    /** Converts one Word/Excel file to PDF. {@code fileName} must keep its extension — LibreOffice relies on it. */
    public Mono<byte[]> convertToPdf(byte[] bytes, String fileName) {
        if (!isEnabled()) {
            return Mono.error(new InvalidRecord("Document conversion is not configured"));
        }
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("files", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);

        return webClient.post()
                .uri(baseUrl + "/forms/libreoffice/convert")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(timeout)
                .doOnSuccess(pdf -> log.info("Converted {} to PDF ({} bytes)", fileName, pdf == null ? 0 : pdf.length))
                .onErrorMap(e -> !(e instanceof InvalidRecord), e -> {
                    log.error("Converting {} to PDF failed: {}", fileName, e.getMessage());
                    return new InvalidRecord("Could not convert the document to PDF for signing. Please try again.");
                });
    }
}
