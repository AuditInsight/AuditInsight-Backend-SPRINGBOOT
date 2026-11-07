package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import com.diana.auditinsightbackendspringboot.Models.EvidenceSignature;
import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceDocumentVersionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignedVersionServiceTest {

    @Mock private EvidenceRepository evidenceRepo;
    @Mock private EvidenceSignatureRepository signatureRepo;
    @Mock private EvidenceDocumentVersionRepository versionRepo;
    @Mock private SignedDocumentService signedDocumentService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private OrgAccessService orgAccessService;
    @Mock private AuditTrailService auditTrail;

    private SignedVersionService service;

    private static final String EMAIL = "signer@test.com";
    private static final byte[] BASE = "original-pdf".getBytes(StandardCharsets.UTF_8);
    private static final Long USER_ID = 7L;
    private static final String STORED_URL = "https://res.cloudinary.com/demo/raw/upload/v1/auditinsight/org/signed/a.pdf";
    private final UUID orgId = UUID.randomUUID();
    private final UUID evidenceId = UUID.randomUUID();
    private Evidence evidence;

    @BeforeEach
    void setUp() {
        service = new SignedVersionService(evidenceRepo, signatureRepo, versionRepo,
                signedDocumentService, cloudinaryService, orgAccessService, auditTrail,
                new SigningModeService(new DocumentConversionService("", 90)));
        // Route the "latest of this kind" lookup through the plain latest-version stub each test sets up.
        lenient().when(versionRepo.findFirstByEvidenceIdAndRenditionOrderByVersionNoDesc(any(), any()))
                .thenAnswer(inv -> versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(inv.getArgument(0)));
        lenient().when(auditTrail.record(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        evidence = new Evidence();
        evidence.setId(evidenceId);
        evidence.setOrganisationId(orgId);
        evidence.setDocumentName("contract.pdf");
        evidence.setFileType("pdf");

        User user = new User();
        user.setId(USER_ID);
        lenient().when(evidenceRepo.findById(evidenceId)).thenReturn(Mono.just(evidence));
        lenient().when(orgAccessService.resolveGatedMember(orgId, EMAIL))
                .thenReturn(Mono.just(new OrgAccessService.OrgMemberContext(user, new OrganisationMember())));
        lenient().when(versionRepo.save(any())).thenAnswer(inv -> {
            EvidenceDocumentVersion v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return Mono.just(v);
        });
    }

    @Test
    void serve_firstTime_rendersStoresAndServesVersionOne() {
        byte[] pdf = bytes("signed-v1");
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId)).thenReturn(Mono.empty());
        when(signedDocumentService.baseDocument(evidence)).thenReturn(Mono.just(BASE));
        when(signedDocumentService.stampOnto(evidence, BASE, 0)).thenReturn(Mono.just(rendered(pdf, 1)));
        when(cloudinaryService.upload(pdf, orgId + "/signed", "pdf")).thenReturn(Mono.just(STORED_URL));

        StepVerifier.create(service.serve(EMAIL, evidenceId))
                .assertNext(served -> {
                    assertEquals(1, served.versionNo());
                    assertEquals(SignedVersionService.sha256Hex(pdf), served.sha256());
                    assertEquals("contract-signed.pdf", served.fileName());
                    assertEquals(new String(pdf, StandardCharsets.UTF_8), new String(served.bytes(), StandardCharsets.UTF_8));
                })
                .verifyComplete();

        ArgumentCaptor<EvidenceDocumentVersion> saved = ArgumentCaptor.forClass(EvidenceDocumentVersion.class);
        verify(versionRepo).save(saved.capture());
        assertEquals(1, saved.getValue().getVersionNo());
        assertEquals(1, saved.getValue().getSignatureCount());
        assertEquals(STORED_URL, saved.getValue().getFileUrl());
        assertEquals(USER_ID, saved.getValue().getCreatedBy());
        assertEquals((long) pdf.length, saved.getValue().getSizeBytes());
    }

    @Test
    void serve_currentVersionExists_servesStoredBytesWithoutRendering() {
        byte[] stored = bytes("stored-v2");
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(2L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId))
                .thenReturn(Mono.just(version(2, 2, stored)));
        when(cloudinaryService.downloadOriginal(STORED_URL)).thenReturn(Mono.just(stored));

        StepVerifier.create(service.serve(EMAIL, evidenceId))
                .assertNext(served -> {
                    assertEquals(2, served.versionNo());
                    assertEquals(SignedVersionService.sha256Hex(stored), served.sha256());
                })
                .verifyComplete();

        verify(signedDocumentService, never()).stampOnto(any(), any(), anyInt());
        verify(cloudinaryService, never()).upload(any(), anyString(), anyString());
    }

    @Test
    void serve_previewThenDownload_returnIdenticalBytes() {
        byte[] stored = bytes("stored-v1");
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId))
                .thenReturn(Mono.just(version(1, 1, stored)));
        when(cloudinaryService.downloadOriginal(STORED_URL)).thenReturn(Mono.just(stored));

        SignedVersionService.ServedVersion preview = service.serve(EMAIL, evidenceId).block();
        SignedVersionService.ServedVersion download = service.serve(EMAIL, evidenceId).block();

        assertEquals(preview.sha256(), download.sha256());
        assertEquals(true, Arrays.equals(preview.bytes(), download.bytes()));
    }

    @Test
    void serve_newSignatureSinceLastVersion_stampsOnlyTheNewSignatureOntoTheStoredVersion() {
        byte[] storedV1 = bytes("stored-v1");
        byte[] pdf = bytes("signed-v2");
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(2L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId))
                .thenReturn(Mono.just(version(1, 1, storedV1)));
        when(cloudinaryService.downloadOriginal(STORED_URL)).thenReturn(Mono.just(storedV1));
        // Built on top of v1 (which already holds signature #1), so earlier stamps never move.
        when(signedDocumentService.stampOnto(evidence, storedV1, 1)).thenReturn(Mono.just(rendered(pdf, 2)));
        when(cloudinaryService.upload(pdf, orgId + "/signed", "pdf")).thenReturn(Mono.just(STORED_URL));

        StepVerifier.create(service.serve(EMAIL, evidenceId))
                .assertNext(served -> assertEquals(2, served.versionNo()))
                .verifyComplete();
        verify(signedDocumentService, never()).baseDocument(any());
    }

    @Test
    void serve_otherRequestStoredTheVersionWhileRendering_adoptsItInsteadOfSavingASecondCopy() {
        byte[] ours = bytes("our-render");
        byte[] theirs = bytes("their-render");
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        // Lookups 1-2 (is there a current version? what do we build on?): nothing stored yet.
        // Lookup 3 (just before saving): the other request won.
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(version(1, 1, theirs)));
        when(signedDocumentService.baseDocument(evidence)).thenReturn(Mono.just(BASE));
        when(signedDocumentService.stampOnto(evidence, BASE, 0)).thenReturn(Mono.just(rendered(ours, 1)));
        when(cloudinaryService.upload(ours, orgId + "/signed", "pdf")).thenReturn(Mono.just("https://res.cloudinary.com/x"));
        when(cloudinaryService.downloadOriginal(STORED_URL)).thenReturn(Mono.just(theirs));

        StepVerifier.create(service.serve(EMAIL, evidenceId))
                .assertNext(served -> assertEquals(SignedVersionService.sha256Hex(theirs), served.sha256()))
                .verifyComplete();
        verify(versionRepo, never()).save(any());
    }

    @Test
    void serve_storedFileNoLongerMatchesItsHash_failsIntegrityCheck() {
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId))
                .thenReturn(Mono.just(version(1, 1, bytes("original-bytes"))));
        when(cloudinaryService.downloadOriginal(STORED_URL)).thenReturn(Mono.just(bytes("tampered-bytes")));

        StepVerifier.create(service.serve(EMAIL, evidenceId))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("integrity"))
                .verify();
    }

    @Test
    void serve_notSignedYet_returnsError() {
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.serve(EMAIL, evidenceId))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("not been signed"))
                .verify();
    }

    @Test
    void createVersionAfterSigning_linksSignatureToStoredVersion() {
        byte[] pdf = bytes("signed-v1");
        UUID signatureId = UUID.randomUUID();
        EvidenceSignature signature = new EvidenceSignature();
        signature.setId(signatureId);
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId)).thenReturn(Mono.empty());
        when(signedDocumentService.baseDocument(evidence)).thenReturn(Mono.just(BASE));
        when(signedDocumentService.stampOnto(evidence, BASE, 0)).thenReturn(Mono.just(rendered(pdf, 1)));
        when(cloudinaryService.upload(pdf, orgId + "/signed", "pdf")).thenReturn(Mono.just(STORED_URL));
        when(versionRepo.findById(any(UUID.class))).thenAnswer(inv -> Mono.just(version(1, 1, pdf)));
        when(signatureRepo.findById(signatureId)).thenReturn(Mono.just(signature));
        when(signatureRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        service.createVersionAfterSigning(evidence, signatureId, USER_ID);

        ArgumentCaptor<EvidenceSignature> linked = ArgumentCaptor.forClass(EvidenceSignature.class);
        verify(signatureRepo, timeout(2000)).save(linked.capture());
        assertEquals(true, linked.getValue().getDocumentVersionId() != null);
    }

    @Test
    void createVersionAfterSigning_storageFails_doesNotThrowAndLeavesSignatureUnlinked() {
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId)).thenReturn(Mono.empty());
        when(signedDocumentService.baseDocument(evidence)).thenReturn(Mono.just(BASE));
        when(signedDocumentService.stampOnto(evidence, BASE, 0)).thenReturn(Mono.just(rendered(bytes("x"), 1)));
        when(cloudinaryService.upload(any(), anyString(), eq("pdf")))
                .thenReturn(Mono.error(new RuntimeException("Cloudinary down")));

        service.createVersionAfterSigning(evidence, UUID.randomUUID(), USER_ID);

        verify(cloudinaryService, timeout(2000)).upload(any(), anyString(), eq("pdf"));
        verify(signatureRepo, never()).save(any());
    }

    @Test
    void serve_wordDocument_rendersCertificateFromScratch() {
        evidence.setFileType("docx");
        byte[] certificate = bytes("certificate");
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId)).thenReturn(Mono.empty());
        when(signedDocumentService.renderSigned(evidence)).thenReturn(Mono.just(rendered(certificate, 1)));
        when(cloudinaryService.upload(certificate, orgId + "/signed", "pdf")).thenReturn(Mono.just(STORED_URL));

        StepVerifier.create(service.serve(EMAIL, evidenceId))
                .assertNext(served -> assertEquals(1, served.versionNo()))
                .verifyComplete();
        verify(signedDocumentService, never()).baseDocument(any());
    }

    @Test
    void signingBase_unsignedDocument_returnsVersionZeroWithItsHash() {
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(0L));
        when(signedDocumentService.baseDocument(evidence)).thenReturn(Mono.just(BASE));

        StepVerifier.create(service.signingBase(EMAIL, evidenceId))
                .assertNext(base -> {
                    assertEquals(0, base.versionNo());
                    assertEquals(SignedVersionService.sha256Hex(BASE), base.sha256());
                    assertArrayEquals(BASE, base.bytes());
                })
                .verifyComplete();
        verify(versionRepo, never()).save(any());
    }

    @Test
    void signingBase_signedDocument_returnsCurrentStoredVersion() {
        byte[] stored = bytes("stored-v1");
        when(signatureRepo.countByEvidenceId(evidenceId)).thenReturn(Mono.just(1L));
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidenceId))
                .thenReturn(Mono.just(version(1, 1, stored)));
        when(cloudinaryService.downloadOriginal(STORED_URL)).thenReturn(Mono.just(stored));

        StepVerifier.create(service.signingBase(EMAIL, evidenceId))
                .assertNext(base -> {
                    assertEquals(1, base.versionNo());
                    assertArrayEquals(stored, base.bytes());
                })
                .verifyComplete();
    }

    @Test
    void signingBase_excelDocumentWithoutConverter_isRejected() {
        evidence.setFileType("xlsx");

        StepVerifier.create(service.signingBase(EMAIL, evidenceId))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("signature certificate"))
                .verify();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static SignedDocumentService.SignedDocument rendered(byte[] pdf, int signatureCount) {
        return new SignedDocumentService.SignedDocument(pdf, "contract-signed.pdf", "application/pdf", signatureCount, false);
    }

    private EvidenceDocumentVersion version(int versionNo, int signatureCount, byte[] content) {
        EvidenceDocumentVersion v = new EvidenceDocumentVersion();
        v.setId(UUID.randomUUID());
        v.setEvidenceId(evidenceId);
        v.setOrganisationId(orgId);
        v.setVersionNo(versionNo);
        v.setSignatureCount(signatureCount);
        v.setFileUrl(STORED_URL);
        v.setFileName("contract-signed.pdf");
        v.setSha256(SignedVersionService.sha256Hex(content));
        v.setSizeBytes((long) content.length);
        return v;
    }
}
