package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceSignature;
import com.diana.auditinsightbackendspringboot.Models.EvidenceSignaturePlacement;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignaturePlacementRepository;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Builds signed renditions of an evidence document.
 * <ul>
 *   <li>PDF / PNG / JPG — signatures are stamped onto the document: where the signer placed them, or (when no
 *       position was chosen) in a "Signatures" block below the last page's content or on a new page. Images are
 *       first put on their own A4 page. New signatures are added onto the previous signed version
 *       ({@link #stampOnto}), so earlier stamps never move.</li>
 *   <li>DOCX / XLS / XLSX — these formats cannot carry a stamp, so a separate signature-certificate PDF is returned.</li>
 * </ul>
 * This is a visual record of who signed and when; it is not a cryptographic PDF signature.
 */
@Service
@Slf4j
public class SignedDocumentService {

    /** A freshly rendered signed document; {@code signatureCount} is how many signatures it contains. */
    public record SignedDocument(byte[] bytes, String fileName, String contentType, int signatureCount) {}

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");
    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";
    private static final float MARGIN = 50f;
    private static final float BLOCK_HEIGHT = 120f;
    private static final float COLUMN_WIDTH = 255f;
    private static final float BOTTOM_MARGIN = 40f;
    private static final float GAP_BELOW_CONTENT = 18f;
    private static final float INLINE_HEADER_HEIGHT = 34f;

    private final EvidenceSignatureRepository signatureRepo;
    private final EvidenceSignaturePlacementRepository placementRepo;
    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;

    public SignedDocumentService(EvidenceSignatureRepository signatureRepo,
                                 EvidenceSignaturePlacementRepository placementRepo,
                                 UserRepository userRepo,
                                 CloudinaryService cloudinaryService) {
        this.signatureRepo = signatureRepo;
        this.placementRepo = placementRepo;
        this.userRepo = userRepo;
        this.cloudinaryService = cloudinaryService;
    }

    /** One signer as printed on the document; {@code placements} is empty when they didn't choose a position. */
    private record Signer(String name, String title, String email, String typedName, byte[] image, String signedAt,
                          List<EvidenceSignaturePlacement> placements) {}

    /** PDFs and PNG/JPG images can carry signatures on the document itself; Word and Excel files cannot. */
    public static boolean isStampable(String fileType) {
        String type = fileType == null ? "" : fileType.toLowerCase();
        return type.equals("pdf") || type.equals("png") || type.equals("jpg") || type.equals("jpeg");
    }

    /**
     * Version 0 of a stampable document as a PDF — the page geometry signers place signatures on. A PDF is the
     * original file unchanged; an image is put on its own A4 page (always the same layout, so placements stay put).
     */
    public Mono<byte[]> baseDocument(Evidence ev) {
        if (!isStampable(ev.getFileType())) {
            return Mono.error(new InvalidRecord(
                    "Word and Excel files can't have a signature placed on them; sign them to get a signature certificate"));
        }
        boolean pdf = "pdf".equalsIgnoreCase(ev.getFileType());
        return download(ev.getFileUpload())
                .flatMap(bytes -> pdf ? Mono.just(bytes) : Mono.fromCallable(() -> {
                    try (PDDocument doc = new PDDocument()) {
                        addFullImagePage(doc, bytes, ev.getDocumentName());
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        doc.save(out);
                        return out.toByteArray();
                    }
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * Adds every signature after the first {@code alreadyIncluded} (in signing order) onto {@code basePdf}, which
     * must already contain those first signatures. Placed signatures are stamped where the signer put them; the
     * rest go into a "Signatures" block below the last page's content or on a new page.
     */
    public Mono<SignedDocument> stampOnto(Evidence ev, byte[] basePdf, int alreadyIncluded) {
        return loadSigners(ev.getId()).flatMap(signers -> {
            if (signers.isEmpty()) {
                return Mono.<SignedDocument>error(new InvalidRecord(
                        "This document has not been signed yet, so there is nothing to add to it"));
            }
            List<Signer> pending = signers.subList(Math.min(alreadyIncluded, signers.size()), signers.size());
            return Mono.fromCallable(() -> {
                try (PDDocument doc = Loader.loadPDF(basePdf)) {
                    List<Signer> unplaced = new ArrayList<>();
                    for (Signer s : pending) {
                        boolean placedAny = false;
                        for (EvidenceSignaturePlacement p : s.placements()) {
                            if (p.getPageIndex() != null && p.getPageIndex() < doc.getNumberOfPages()) {
                                drawPlaced(doc, s, p);
                                placedAny = true;
                            }
                        }
                        if (!placedAny) unplaced.add(s); // no (valid) position chosen: use the signature block
                    }
                    if (!unplaced.isEmpty() && doc.getNumberOfPages() > 0) {
                        addSignaturePages(doc, ev.getDocumentName(), unplaced,
                                doc.getPage(doc.getNumberOfPages() - 1), lastPageContentBottom(doc));
                    } else if (!unplaced.isEmpty()) {
                        addSignaturePages(doc, ev.getDocumentName(), unplaced, null, null);
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    doc.save(out);
                    return new SignedDocument(out.toByteArray(), stripExtension(ev.getDocumentName()) + "-signed.pdf",
                            "application/pdf", signers.size());
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    /**
     * Renders the document with every signature it currently has, from scratch: the original plus all signatures
     * for PDFs/images, or a signature certificate for Word/Excel files. Access checks and storage are the
     * caller's job (see SignedVersionService), which stores the result so Preview and Download serve identical bytes.
     */
    public Mono<SignedDocument> renderSigned(Evidence ev) {
        if (isStampable(ev.getFileType())) {
            return baseDocument(ev).flatMap(base -> stampOnto(ev, base, 0));
        }
        return loadSigners(ev.getId())
                .flatMap(signers -> {
                    if (signers.isEmpty()) {
                        return Mono.<SignedDocument>error(new InvalidRecord(
                                "This document has not been signed yet, so there is nothing to add to it"));
                    }
                    return renderCertificate(ev, signers);
                });
    }

    private Mono<List<Signer>> loadSigners(UUID evidenceId) {
        return signatureRepo.findAllByEvidenceIdOrderBySignedAtAsc(evidenceId)
                .concatMap(s -> Mono.zip(
                        userRepo.findById(s.getSignerId()).map(u -> u.getUsername()).defaultIfEmpty(""),
                        placementRepo.findAllBySignatureId(s.getId()).collectList())
                        .map(t -> toSigner(s, t.getT1(), t.getT2())))
                .collectList();
    }

    private Signer toSigner(EvidenceSignature s, String email, List<EvidenceSignaturePlacement> placements) {
        return new Signer(s.getSignerName(), s.getSignerTitle(), email, s.getTypedName(), decodeImage(s.getSignatureImage()),
                s.getSignedAt() == null ? "" : TIME_FORMAT.format(toUtc(s.getSignedAt())), placements);
    }

    /**
     * signed_at is written with {@code LocalDateTime.now()}, i.e. in the server's own time zone, while the page
     * labels the time as UTC — convert it so the label is true wherever the server runs.
     */
    static LocalDateTime toUtc(LocalDateTime serverLocal) {
        return serverLocal.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private byte[] decodeImage(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(PNG_DATA_URL_PREFIX)) return null;
        try {
            return Base64.getDecoder().decode(dataUrl.substring(PNG_DATA_URL_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Word/Excel: a standalone certificate PDF listing every signer, since the file itself can't be stamped. */
    private Mono<SignedDocument> renderCertificate(Evidence ev, List<Signer> signers) {
        return Mono.fromCallable(() -> {
            try (PDDocument doc = new PDDocument()) {
                addSignaturePages(doc, ev.getDocumentName(), signers, null, null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.save(out);
                return new SignedDocument(out.toByteArray(), stripExtension(ev.getDocumentName()) + "-signatures.pdf",
                        "application/pdf", signers.size());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<byte[]> download(String url) {
        if (url == null || url.isBlank()) {
            return Mono.error(new InvalidRecord("This evidence has no stored file"));
        }
        return cloudinaryService.downloadOriginal(url)
                .onErrorMap(e -> !(e instanceof InvalidRecord),
                        e -> new InvalidRecord("Could not fetch the original file: " + e.getMessage()));
    }

    // ── PDF drawing ───────────────────────────────────────────────────────────────

    /**
     * Puts the image on its own A4 page, as large as the margins allow (never enlarged), top-aligned. The layout
     * never depends on how many people signed, so a signature placed over the image stays where it was put.
     */
    private void addFullImagePage(PDDocument doc, byte[] imageBytes, String documentName) throws IOException {
        PDImageXObject image = PDImageXObject.createFromByteArray(doc, imageBytes, documentName);
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float maxW = page.getMediaBox().getWidth() - 2 * MARGIN;
        float maxH = page.getMediaBox().getHeight() - 2 * MARGIN;
        float scale = Math.min(1f, Math.min(maxW / image.getWidth(), maxH / image.getHeight()));
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        float bottom = page.getMediaBox().getHeight() - MARGIN - h;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(image, (page.getMediaBox().getWidth() - w) / 2, bottom, w, h);
        }
    }

    /**
     * Stamps one placed signature: the drawn image (or typed name) fitted inside the chosen box, upright as the
     * viewer sees the page, with a small "name · time" caption just below it (or above, at the page's bottom edge).
     */
    private void drawPlaced(PDDocument doc, Signer s, EvidenceSignaturePlacement p) throws IOException {
        PDPage page = doc.getPage(p.getPageIndex());
        PDRectangle box = page.getCropBox();
        int rotation = PlacementGeometry.normalize(page.getRotation());
        float pageW = PlacementGeometry.displayWidth(box, rotation);
        float pageH = PlacementGeometry.displayHeight(box, rotation);
        float dx = p.getX().floatValue() * pageW;
        float dy = p.getY().floatValue() * pageH;
        float dw = Math.min(p.getWidth().floatValue(), 1f - p.getX().floatValue()) * pageW;
        float dh = Math.min(p.getHeight().floatValue(), 1f - p.getY().floatValue()) * pageH;

        PDType1Font script = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            boolean drewImage = false;
            if (s.image() != null) {
                try {
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, s.image(), "signature");
                    float scale = Math.min(dw / img.getWidth(), dh / img.getHeight());
                    float w = img.getWidth() * scale, h = img.getHeight() * scale;
                    cs.drawImage(img, PlacementGeometry.boxMatrix(box, rotation,
                            dx + (dw - w) / 2, dy + (dh - h) / 2, w, h));
                    drewImage = true;
                } catch (IOException e) {
                    log.warn("Unreadable signature image for {}, stamping the typed name instead", s.email());
                }
            }
            if (!drewImage) {
                String text = sanitize(firstNonBlank(s.typedName(), s.name()));
                float widthAt1 = Math.max(script.getStringWidth(text) / 1000f, 0.01f);
                float size = Math.max(4f, Math.min(dh * 0.7f, dw * 0.95f / widthAt1));
                float textW = widthAt1 * size;
                cs.beginText();
                cs.setFont(script, size);
                cs.setTextMatrix(PlacementGeometry.textMatrix(box, rotation,
                        dx + (dw - textW) / 2, dy + dh / 2 + size * 0.3f));
                cs.showText(text);
                cs.endText();
            }

            String caption = sanitize(s.name() + (s.signedAt().isBlank() ? "" : " · " + s.signedAt()));
            float captionSize = 6.5f;
            float captionY = dy + dh + 8f <= pageH - 2f ? dy + dh + 8f : Math.max(dy - 2f, captionSize + 1f);
            cs.setNonStrokingColor(Color.DARK_GRAY);
            cs.beginText();
            cs.setFont(regular, captionSize);
            cs.setTextMatrix(PlacementGeometry.textMatrix(box, rotation, dx, captionY));
            cs.showText(caption);
            cs.endText();
        }
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
