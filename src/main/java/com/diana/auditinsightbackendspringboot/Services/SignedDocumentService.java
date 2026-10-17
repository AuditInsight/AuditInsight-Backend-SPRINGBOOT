package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceSignature;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignatureRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Builds the "download with signatures" version of an evidence document: the original file followed by a
 * signature page listing every signer (drawn or typed signature, name, email and time).
 * <ul>
 *   <li>PDF — signature page(s) are appended to the original document.</li>
 *   <li>PNG / JPG — wrapped into a PDF page, followed by the signature page(s).</li>
 *   <li>XLS / XLSX — these formats cannot carry a stamp, so a separate signature-certificate PDF is returned.</li>
 * </ul>
 * This is a visual record of who signed and when; it is not a cryptographic PDF signature.
 */
@Service
@Slf4j
public class SignedDocumentService {

    public record SignedDocument(byte[] bytes, String fileName, String contentType) {}

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");
    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";
    private static final float MARGIN = 50f;
    private static final float BLOCK_HEIGHT = 120f;
    private static final int MAX_DOWNLOAD_BYTES = 30 * 1024 * 1024;

    private final EvidenceRepository evidenceRepo;
    private final EvidenceSignatureRepository signatureRepo;
    private final UserRepository userRepo;
    private final OrgAccessService orgAccessService;
    private final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_DOWNLOAD_BYTES))
            .build();

    public SignedDocumentService(EvidenceRepository evidenceRepo,
                                 EvidenceSignatureRepository signatureRepo,
                                 UserRepository userRepo,
                                 OrgAccessService orgAccessService) {
        this.evidenceRepo = evidenceRepo;
        this.signatureRepo = signatureRepo;
        this.userRepo = userRepo;
        this.orgAccessService = orgAccessService;
    }

    /** One signer as printed on the signature page. */
    private record Signer(String name, String email, String typedName, byte[] image, String signedAt) {}

    public Mono<SignedDocument> build(String requesterEmail, UUID evidenceId) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")))
                .flatMap(ev -> orgAccessService.resolveGatedMember(ev.getOrganisationId(), requesterEmail)
                        .then(loadSigners(evidenceId))
                        .flatMap(signers -> {
                            if (signers.isEmpty()) {
                                return Mono.<SignedDocument>error(new InvalidRecord(
                                        "This document has not been signed yet, so there is nothing to add to it"));
                            }
                            return render(ev, signers);
                        }));
    }

    private Mono<List<Signer>> loadSigners(UUID evidenceId) {
        return signatureRepo.findAllByEvidenceIdOrderBySignedAtAsc(evidenceId)
                .concatMap(s -> userRepo.findById(s.getSignerId())
                        .map(u -> u.getUsername())
                        .defaultIfEmpty("")
                        .map(email -> toSigner(s, email)))
                .collectList();
    }

    private Signer toSigner(EvidenceSignature s, String email) {
        return new Signer(s.getSignerName(), email, s.getTypedName(), decodeImage(s.getSignatureImage()),
                s.getSignedAt() == null ? "" : TIME_FORMAT.format(s.getSignedAt()));
    }

    private byte[] decodeImage(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(PNG_DATA_URL_PREFIX)) return null;
        try {
            return Base64.getDecoder().decode(dataUrl.substring(PNG_DATA_URL_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Mono<SignedDocument> render(Evidence ev, List<Signer> signers) {
        String type = ev.getFileType() == null ? "" : ev.getFileType().toLowerCase();
        String baseName = stripExtension(ev.getDocumentName());

        boolean stampable = type.equals("pdf") || type.equals("png") || type.equals("jpg") || type.equals("jpeg");
        Mono<byte[]> original = stampable ? download(ev.getFileUpload()) : Mono.just(new byte[0]);

        return original.flatMap(bytes -> Mono.fromCallable(() -> {
            try (PDDocument doc = type.equals("pdf") && bytes.length > 0 ? Loader.loadPDF(bytes) : new PDDocument()) {
                if (type.equals("png") || type.equals("jpg") || type.equals("jpeg")) {
                    addImagePage(doc, bytes, ev.getDocumentName());
                }
                addSignaturePages(doc, ev.getDocumentName(), signers);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.save(out);
                String name = stampable ? baseName + "-signed.pdf" : baseName + "-signatures.pdf";
                return new SignedDocument(out.toByteArray(), name, "application/pdf");
            }
        }).subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<byte[]> download(String url) {
        if (url == null || url.isBlank()) {
            return Mono.error(new InvalidRecord("This evidence has no stored file"));
        }
        return webClient.get().uri(url).retrieve().bodyToMono(byte[].class)
                .onErrorMap(e -> !(e instanceof InvalidRecord),
                        e -> new InvalidRecord("Could not fetch the original file: " + e.getMessage()));
    }

    // ── PDF drawing ───────────────────────────────────────────────────────────────

    private void addImagePage(PDDocument doc, byte[] imageBytes, String documentName) throws IOException {
        PDImageXObject image = PDImageXObject.createFromByteArray(doc, imageBytes, documentName);
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float maxW = page.getMediaBox().getWidth() - 2 * MARGIN;
        float maxH = page.getMediaBox().getHeight() - 2 * MARGIN;
        float scale = Math.min(1f, Math.min(maxW / image.getWidth(), maxH / image.getHeight()));
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(image, (page.getMediaBox().getWidth() - w) / 2, page.getMediaBox().getHeight() - MARGIN - h, w, h);
        }
    }

    private void addSignaturePages(PDDocument doc, String documentName, List<Signer> signers) throws IOException {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font script = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

        PDPage page = null;
        PDPageContentStream cs = null;
        float y = 0;
        try {
            for (Signer s : signers) {
                if (page == null || y - BLOCK_HEIGHT < MARGIN) {
                    if (cs != null) cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - MARGIN;
                    text(cs, bold, 16, MARGIN, y, "Signatures");
                    y -= 18;
                    text(cs, regular, 9, MARGIN, y, "Document: " + documentName);
                    y -= 30;
                }
                drawSigner(doc, cs, s, y, page.getMediaBox().getWidth(), bold, regular, script);
                y -= BLOCK_HEIGHT;
            }
        } finally {
            if (cs != null) cs.close();
        }
    }

    private void drawSigner(PDDocument doc, PDPageContentStream cs, Signer s, float top, float pageWidth,
                            PDType1Font bold, PDType1Font regular, PDType1Font script) throws IOException {
        float sigW = 190f;
        float lineY = top - 62;

        if (s.image() != null) {
            try {
                PDImageXObject img = PDImageXObject.createFromByteArray(doc, s.image(), "signature");
                float scale = Math.min(sigW / img.getWidth(), 52f / img.getHeight());
                cs.drawImage(img, MARGIN, lineY + 4, img.getWidth() * scale, img.getHeight() * scale);
            } catch (IOException e) {
                log.warn("Skipping unreadable signature image for {}", s.email());
                text(cs, script, 22, MARGIN, lineY + 10, firstNonBlank(s.typedName(), s.name()));
            }
        } else {
            text(cs, script, 22, MARGIN, lineY + 10, firstNonBlank(s.typedName(), s.name()));
        }

        cs.setStrokingColor(Color.GRAY);
        cs.setLineWidth(0.8f);
        cs.moveTo(MARGIN, lineY);
        cs.lineTo(MARGIN + sigW + 40, lineY);
        cs.stroke();

        text(cs, bold, 11, MARGIN, lineY - 15, s.name());
        text(cs, regular, 10, MARGIN, lineY - 29, s.email());
        text(cs, regular, 9, MARGIN, lineY - 42, "Signed on " + s.signedAt());
    }

    private void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(value));
        cs.endText();
    }

    /** The built-in PDF fonts only cover Latin-1; replace anything else so showText never throws. */
    private String sanitize(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            sb.append((c >= 32 && c < 127) || (c >= 160 && c <= 255) ? c : (char) 63);
        }
        return sb.toString();
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private String stripExtension(String name) {
        if (name == null || name.isBlank()) return "document";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
