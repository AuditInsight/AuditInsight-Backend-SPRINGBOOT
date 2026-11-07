package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceSignature;
import com.diana.auditinsightbackendspringboot.Models.EvidenceSignaturePlacement;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignaturePlacementRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignatureRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignedDocumentServiceTest {

    private final TimeZone originalZone = TimeZone.getDefault();

    private final EvidenceSignatureRepository signatureRepo = mock(EvidenceSignatureRepository.class);
    private final EvidenceSignaturePlacementRepository placementRepo = mock(EvidenceSignaturePlacementRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final CloudinaryService cloudinaryService = mock(CloudinaryService.class);
    private SignedDocumentService service;
    private Evidence evidence;

    @BeforeEach
    void setUp() {
        service = new SignedDocumentService(signatureRepo, placementRepo, userRepo, cloudinaryService);
        evidence = new Evidence();
        evidence.setId(UUID.randomUUID());
        evidence.setOrganisationId(UUID.randomUUID());
        evidence.setDocumentName("contract.pdf");
        evidence.setFileType("pdf");
        evidence.setFileUpload("https://res.cloudinary.com/demo/raw/upload/v1/contract.pdf");
        User user = new User();
        user.setUsername("signer@test.com");
        when(userRepo.findById(any(Long.class))).thenReturn(Mono.just(user));
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    // ── signing time ─────────────────────────────────────────────────────────────

    @Test
    void toUtc_serverInKigali_shiftsBackTwoHours() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Kigali")); // UTC+2
        assertEquals(LocalDateTime.of(2026, 11, 7, 8, 30),
                SignedDocumentService.toUtc(LocalDateTime.of(2026, 11, 7, 10, 30)));
    }

    @Test
    void toUtc_serverAlreadyInUtc_leavesTimeUnchanged() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 7, 10, 30);
        assertEquals(signedAt, SignedDocumentService.toUtc(signedAt));
    }

    // ── stamping ─────────────────────────────────────────────────────────────────

    @Test
    void stampOnto_placedSignature_appearsInsideTheChosenBoxWithoutAddingPages() throws IOException {
        EvidenceSignature sig = signature("Jane Doe", "J. Doe");
        givenSignatures(sig);
        givenPlacements(sig, placement(sig, 0, 0.10, 0.10, 0.40, 0.08));

        byte[] out = service.stampOnto(evidence, blankA4(1), 0).block().bytes();

        try (PDDocument doc = Loader.loadPDF(out)) {
            assertEquals(1, doc.getNumberOfPages());
            PDRectangle page = doc.getPage(0).getCropBox();
            String inBox = textIn(doc, 0, 0.10f * page.getWidth(), 0.10f * page.getHeight(),
                    0.40f * page.getWidth(), 0.08f * page.getHeight());
            assertTrue(inBox.contains("J. Doe"), "typed signature should be inside the box, got: " + inBox);
            String all = new PDFTextStripper().getText(doc);
            assertTrue(all.contains("Jane Doe"), "caption with the signer's name should be on the page");
            assertFalse(all.contains("Signatures"), "a placed signature must not add a signature block");
        }
    }

    @Test
    void stampOnto_placedSignatureOnSecondPage_staysOnThatPage() throws IOException {
        EvidenceSignature sig = signature("Jane Doe", "J. Doe");
        givenSignatures(sig);
        givenPlacements(sig, placement(sig, 1, 0.5, 0.8, 0.3, 0.06));

        byte[] out = service.stampOnto(evidence, blankA4(2), 0).block().bytes();

        try (PDDocument doc = Loader.loadPDF(out)) {
            assertEquals(2, doc.getNumberOfPages());
            assertFalse(pageText(doc, 1).contains("J. Doe"));
            assertTrue(pageText(doc, 2).contains("J. Doe"));
        }
    }

    @Test
    void stampOnto_signatureWithoutPosition_goesIntoTheSignatureBlock() throws IOException {
        EvidenceSignature sig = signature("Jane Doe", "J. Doe");
        givenSignatures(sig);
        givenPlacements(sig);

        byte[] out = service.stampOnto(evidence, blankA4(1), 0).block().bytes();

        try (PDDocument doc = Loader.loadPDF(out)) {
            String all = new PDFTextStripper().getText(doc);
            assertTrue(all.contains("Signatures"));
            assertTrue(all.contains("Jane Doe"));
        }
    }

    @Test
    void stampOnto_positionOnMissingPage_fallsBackToTheSignatureBlock() throws IOException {
        EvidenceSignature sig = signature("Jane Doe", "J. Doe");
        givenSignatures(sig);
        givenPlacements(sig, placement(sig, 5, 0.1, 0.1, 0.3, 0.05));

        byte[] out = service.stampOnto(evidence, blankA4(1), 0).block().bytes();

        try (PDDocument doc = Loader.loadPDF(out)) {
            assertTrue(new PDFTextStripper().getText(doc).contains("Signatures"));
        }
    }

    @Test
    void stampOnto_onlyAddsSignaturesTheBaseDoesNotHaveYet() throws IOException {
        EvidenceSignature first = signature("First Signer", "First");
        EvidenceSignature second = signature("Second Signer", "Second");
        givenSignatures(first, second);
        givenPlacements(first, placement(first, 0, 0.1, 0.1, 0.3, 0.05));
        givenPlacements(second, placement(second, 0, 0.1, 0.5, 0.3, 0.05));

        // The base already holds signature #1, so only #2 must be drawn.
        var result = service.stampOnto(evidence, blankA4(1), 1).block();

        assertEquals(2, result.signatureCount());
        try (PDDocument doc = Loader.loadPDF(result.bytes())) {
            String all = new PDFTextStripper().getText(doc);
            assertTrue(all.contains("Second Signer"));
            assertFalse(all.contains("First Signer"));
        }
    }

    @Test
    void baseDocument_image_isPutOnASingleA4Page() throws IOException {
        evidence.setFileType("png");
        evidence.setDocumentName("receipt.png");
        when(cloudinaryService.downloadOriginal(any())).thenReturn(Mono.just(png(400, 300)));

        byte[] base = service.baseDocument(evidence).block();

        try (PDDocument doc = Loader.loadPDF(base)) {
            assertEquals(1, doc.getNumberOfPages());
            assertEquals(PDRectangle.A4.getWidth(), doc.getPage(0).getMediaBox().getWidth(), 0.01);
        }
    }

    @Test
    void baseDocument_pdf_isTheOriginalUnchanged() {
        byte[] original = blankA4(1);
        when(cloudinaryService.downloadOriginal(any())).thenReturn(Mono.just(original));

        assertEquals(original, service.baseDocument(evidence).block());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private EvidenceSignature signature(String name, String typed) {
        EvidenceSignature s = new EvidenceSignature();
        s.setId(UUID.randomUUID());
        s.setEvidenceId(evidence.getId());
        s.setSignerId(1L);
        s.setSignerName(name);
        s.setTypedName(typed);
        s.setSignedAt(LocalDateTime.of(2026, 11, 7, 10, 30));
        return s;
    }

    private EvidenceSignaturePlacement placement(EvidenceSignature s, int page, double x, double y, double w, double h) {
        EvidenceSignaturePlacement p = new EvidenceSignaturePlacement();
        p.setSignatureId(s.getId());
        p.setPageIndex(page);
        p.setX(BigDecimal.valueOf(x));
        p.setY(BigDecimal.valueOf(y));
        p.setWidth(BigDecimal.valueOf(w));
        p.setHeight(BigDecimal.valueOf(h));
        return p;
    }

    private void givenSignatures(EvidenceSignature... signatures) {
        when(signatureRepo.findAllByEvidenceIdOrderBySignedAtAsc(evidence.getId())).thenReturn(Flux.just(signatures));
    }

    private void givenPlacements(EvidenceSignature s, EvidenceSignaturePlacement... placements) {
        when(placementRepo.findAllBySignatureId(s.getId())).thenReturn(Flux.just(placements));
    }

    private static byte[] blankA4(int pages) {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String textIn(PDDocument doc, int pageIndex, float x, float y, float w, float h) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.addRegion("box", new Rectangle2D.Float(x, y, w, h));
        stripper.extractRegions(doc.getPage(pageIndex));
        return stripper.getTextForRegion("box");
    }

    private static String pageText(PDDocument doc, int pageNumber) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(doc);
    }
}
