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
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.Color;
import java.awt.image.BufferedImage;
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
    private static final float COLUMN_WIDTH = 255f;
    private static final float BOTTOM_MARGIN = 40f;
    private static final float GAP_BELOW_CONTENT = 18f;
    private static final float INLINE_HEADER_HEIGHT = 34f;
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
    private record Signer(String name, String title, String email, String typedName, byte[] image, String signedAt) {}

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
        return new Signer(s.getSignerName(), s.getSignerTitle(), email, s.getTypedName(), decodeImage(s.getSignatureImage()),
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
                PDPage lastPage = null;
                Float contentBottom = null;
                if (type.equals("png") || type.equals("jpg") || type.equals("jpeg")) {
                    Placed placed = addImagePage(doc, bytes, ev.getDocumentName(), signers.size());
                    lastPage = placed.page();
                    contentBottom = placed.contentBottomY();
                } else if (type.equals("pdf") && doc.getNumberOfPages() > 0) {
                    lastPage = doc.getPage(doc.getNumberOfPages() - 1);
                    contentBottom = lastPageContentBottom(doc);
                }
                addSignaturePages(doc, ev.getDocumentName(), signers, lastPage, contentBottom);
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

    /** Places the image on its own A4 page; returns the page and the y of the image's bottom edge. */
    private record Placed(PDPage page, float contentBottomY) {}

    private Placed addImagePage(PDDocument doc, byte[] imageBytes, String documentName, int signerCount) throws IOException {
        PDImageXObject image = PDImageXObject.createFromByteArray(doc, imageBytes, documentName);
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float maxW = page.getMediaBox().getWidth() - 2 * MARGIN;
        // Leave room under the image for the signatures (heading + every signer row), but never shrink it below 40% of the page.
        int rows = (signerCount + columnsFor(page.getMediaBox().getWidth()) - 1) / columnsFor(page.getMediaBox().getWidth());
        float reserved = GAP_BELOW_CONTENT + INLINE_HEADER_HEIGHT + rows * BLOCK_HEIGHT + BOTTOM_MARGIN;
        float fullHeight = page.getMediaBox().getHeight() - 2 * MARGIN;
        float maxH = Math.max(fullHeight * 0.4f, page.getMediaBox().getHeight() - MARGIN - reserved);
        float scale = Math.min(1f, Math.min(maxW / image.getWidth(), maxH / image.getHeight()));
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        float bottom = page.getMediaBox().getHeight() - MARGIN - h;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(image, (page.getMediaBox().getWidth() - w) / 2, bottom, w, h);
        }
        return new Placed(page, bottom);
    }

    /**
     * Finds where the last page's content ends by rendering it small and scanning for the lowest non-white row.
     * Returns the y (PDF coordinates, origin bottom-left) of the bottom of the content, or null when the page
     * can't be analysed (rotated page, render failure) — callers then simply start a new page.
     */
    private Float lastPageContentBottom(PDDocument doc) {
        try {
            int index = doc.getNumberOfPages() - 1;
            PDPage page = doc.getPage(index);
            if (page.getRotation() != 0) return null;
            float scale = 0.7f;
            BufferedImage img = new PDFRenderer(doc).renderImage(index, scale);
            PDRectangle box = page.getCropBox();
            int lowest = -1;
            for (int row = img.getHeight() - 1; row >= 0 && lowest < 0; row--) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, row);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (r < 245 || g < 245 || b < 245) {
                        lowest = row;
                        break;
                    }
                }
            }
            if (lowest < 0) return box.getUpperRightY() - MARGIN; // blank page
            return box.getUpperRightY() - (lowest + 1) / scale;
        } catch (Exception e) {
            log.warn("Could not measure the last page, adding a separate signature page: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Draws the signature blocks. When {@code existingPage} is given and enough room is left below its content
     * ({@code contentBottomY}), the first signatures go on that same page; only when it is full does it continue
     * on new pages.
     */
    private void addSignaturePages(PDDocument doc, String documentName, List<Signer> signers,
                                   PDPage existingPage, Float contentBottomY) throws IOException {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font script = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

        PDPage page = null;
        PDPageContentStream cs = null;
        float y = 0;
        int column = 0;
        int columns = 1;
        try {
            if (existingPage != null && contentBottomY != null) {
                float top = contentBottomY - GAP_BELOW_CONTENT;
                float pageWidth = existingPage.getCropBox().getWidth();
                if (top - INLINE_HEADER_HEIGHT - BLOCK_HEIGHT >= BOTTOM_MARGIN) {
                    page = existingPage;
                    cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);
                    columns = columnsFor(pageWidth);
                    cs.setStrokingColor(Color.LIGHT_GRAY);
                    cs.setLineWidth(0.6f);
                    cs.moveTo(MARGIN, top);
                    cs.lineTo(pageWidth - MARGIN, top);
                    cs.stroke();
                    text(cs, bold, 12, MARGIN, top - 18, "Signatures");
                    y = top - INLINE_HEADER_HEIGHT;
                }
            }

            for (Signer s : signers) {
                boolean needNewPage = page == null || (column == 0 && y - BLOCK_HEIGHT < BOTTOM_MARGIN);
                if (needNewPage) {
                    if (cs != null) cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    columns = columnsFor(page.getMediaBox().getWidth());
                    column = 0;
                    y = page.getMediaBox().getHeight() - MARGIN;
                    text(cs, bold, 16, MARGIN, y, "Signatures");
                    y -= 18;
                    text(cs, regular, 9, MARGIN, y, "Document: " + documentName);
                    y -= 30;
                }
                drawSigner(doc, cs, s, MARGIN + column * COLUMN_WIDTH, y, bold, regular, script);
                column++;
                if (column >= columns) {
                    column = 0;
                    y -= BLOCK_HEIGHT;
                }
            }
        } finally {
            if (cs != null) cs.close();
        }
    }

    private int columnsFor(float pageWidth) {
        return pageWidth - 2 * MARGIN >= 2 * COLUMN_WIDTH - 20 ? 2 : 1;
    }

    private void drawSigner(PDDocument doc, PDPageContentStream cs, Signer s, float x, float top,
                            PDType1Font bold, PDType1Font regular, PDType1Font script) throws IOException {
        float sigW = 190f;
        float lineY = top - 62;

        if (s.image() != null) {
            try {
                PDImageXObject img = PDImageXObject.createFromByteArray(doc, s.image(), "signature");
                float scale = Math.min(sigW / img.getWidth(), 52f / img.getHeight());
                cs.drawImage(img, x, lineY + 4, img.getWidth() * scale, img.getHeight() * scale);
            } catch (IOException e) {
                log.warn("Skipping unreadable signature image for {}", s.email());
                text(cs, script, 22, x, lineY + 10, firstNonBlank(s.typedName(), s.name()));
            }
        } else {
            text(cs, script, 22, x, lineY + 10, firstNonBlank(s.typedName(), s.name()));
        }

        cs.setStrokingColor(Color.GRAY);
        cs.setLineWidth(0.8f);
        cs.moveTo(x, lineY);
        cs.lineTo(x + sigW + 40, lineY);
        cs.stroke();

        float textY = lineY - 15;
        text(cs, bold, 11, x, textY, s.name());
        if (s.title() != null && !s.title().isBlank()) {
            textY -= 14;
            text(cs, regular, 10, x, textY, s.title());
        }
        textY -= 14;
        text(cs, regular, 10, x, textY, s.email());
        textY -= 13;
        text(cs, regular, 9, x, textY, "Signed on " + s.signedAt());
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
