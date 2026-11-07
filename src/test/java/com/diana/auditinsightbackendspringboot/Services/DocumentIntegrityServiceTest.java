package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.RequestMeta;
import com.diana.auditinsightbackendspringboot.Enum.AuditEventType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIntegrityServiceTest {

    @Mock private EvidenceRepository evidenceRepo;
    @Mock private EvidenceSignatureRepository signatureRepo;
    @Mock private EvidenceDocumentVersionRepository versionRepo;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private OrgAccessService orgAccessService;
    @Mock private AuditTrailService auditTrail;

    private DocumentIntegrityService service;
    private Evidence evidence;
    private static final String EMAIL = "member@test.com";
    private static final byte[] ORIGINAL = "original-file".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SIGNED_V1 = "signed-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SIGNED_V2 = "signed-v2".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        service = new DocumentIntegrityService(evidenceRepo, signatureRepo, versionRepo, cloudinaryService,
                orgAccessService, auditTrail);
        evidence = new Evidence();
        evidence.setId(UUID.randomUUID());
        evidence.setOrganisationId(UUID.randomUUID());
        evidence.setDocumentName("contract.pdf");
        evidence.setFileType("pdf");
        evidence.setFileUpload("https://res.cloudinary.com/demo/raw/upload/v1/contract.pdf");
        evidence.setOriginalSha256(SignedVersionService.sha256Hex(ORIGINAL));

        User user = new User();
        user.setId(1L);
        lenient().when(evidenceRepo.findById(evidence.getId())).thenReturn(Mono.just(evidence));
        lenient().when(orgAccessService.resolveGatedMember(evidence.getOrganisationId(), EMAIL))
                .thenReturn(Mono.just(new OrgAccessService.OrgMemberContext(user, new OrganisationMember())));
        lenient().when(auditTrail.record(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(versionRepo.findAllByEvidenceIdOrderByVersionNoAsc(evidence.getId()))
                .thenReturn(Flux.just(version(1, 1, SIGNED_V1), version(2, 2, SIGNED_V2)));
        lenient().when(signatureRepo.findAllByEvidenceIdOrderBySignedAtAsc(evidence.getId()))
                .thenReturn(Flux.just(signature("First Signer"), signature("Second Signer")));
    }

    // ── original ─────────────────────────────────────────────────────────────────

    @Test
    void original_matchingFingerprint_isServedAndAudited() {
        when(cloudinaryService.downloadOriginal(evidence.getFileUpload())).thenReturn(Mono.just(ORIGINAL));

        StepVerifier.create(service.original(EMAIL, evidence.getId(), RequestMeta.NONE))
                .assertNext(file -> {
                    assertEquals("application/pdf", file.contentType());
                    assertEquals(SignedVersionService.sha256Hex(ORIGINAL), file.sha256());
                })
                .verifyComplete();
        verify(auditTrail).record(eq(evidence), eq(AuditEventType.ORIGINAL_ACCESSED), any(), any(), any());
    }

    @Test
    void original_changedInStorage_failsIntegrityCheck() {
        when(cloudinaryService.downloadOriginal(evidence.getFileUpload()))
                .thenReturn(Mono.just("tampered".getBytes(StandardCharsets.UTF_8)));

        StepVerifier.create(service.original(EMAIL, evidence.getId(), RequestMeta.NONE))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("integrity"))
                .verify();
    }

    @Test
    void original_olderUploadWithoutFingerprint_recordsItOnFirstAccess() {
        evidence.setOriginalSha256(null);
        when(cloudinaryService.downloadOriginal(evidence.getFileUpload())).thenReturn(Mono.just(ORIGINAL));
        when(evidenceRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.original(EMAIL, evidence.getId(), RequestMeta.NONE))
                .expectNextCount(1)
                .verifyComplete();
        assertEquals(SignedVersionService.sha256Hex(ORIGINAL), evidence.getOriginalSha256());
        verify(evidenceRepo).save(evidence);
    }

    // ── verify ───────────────────────────────────────────────────────────────────

    @Test
    void verify_latestSignedVersion_namesItsSigners() {
        StepVerifier.create(service.verify(EMAIL, evidence.getId(), SIGNED_V2, RequestMeta.NONE))
                .assertNext(r -> {
                    assertEquals("SIGNED_VERSION", r.result());
                    assertEquals(2, r.versionNo());
                    assertTrue(r.latest());
                    assertEquals(2, r.signers().size());
                })
                .verifyComplete();
        verify(auditTrail).record(eq(evidence), eq(AuditEventType.VERIFICATION), any(), any(), any());
    }

    @Test
    void verify_olderSignedVersion_saysANewerOneExists() {
        StepVerifier.create(service.verify(EMAIL, evidence.getId(), SIGNED_V1, RequestMeta.NONE))
                .assertNext(r -> {
                    assertEquals("SIGNED_VERSION", r.result());
                    assertEquals(1, r.versionNo());
                    assertFalse(r.latest());
                    assertEquals(1, r.signers().size());
                    assertEquals("First Signer", r.signers().get(0).name());
                    assertTrue(r.message().contains("v2"));
                })
                .verifyComplete();
    }

    @Test
    void verify_originalUpload_isRecognisedAsTheUnsignedOriginal() {
        StepVerifier.create(service.verify(EMAIL, evidence.getId(), ORIGINAL, RequestMeta.NONE))
                .assertNext(r -> {
                    assertEquals("ORIGINAL", r.result());
                    assertEquals(0, r.versionNo());
                })
                .verifyComplete();
        verify(cloudinaryService, never()).downloadOriginal(any());
    }

    @Test
    void verify_alteredCopy_isReportedAsNoMatch() {
        byte[] altered = "signed-v2 with an edit".getBytes(StandardCharsets.UTF_8);

        StepVerifier.create(service.verify(EMAIL, evidence.getId(), altered, RequestMeta.NONE))
                .assertNext(r -> {
                    assertEquals("NO_MATCH", r.result());
                    assertEquals(null, r.versionNo());
                    assertEquals(SignedVersionService.sha256Hex(altered), r.sha256());
                })
                .verifyComplete();
    }

    @Test
    void verify_emptyFile_isRejected() {
        StepVerifier.create(service.verify(EMAIL, evidence.getId(), new byte[0], RequestMeta.NONE))
                .expectError(InvalidRecord.class)
                .verify();
    }

    // ── versions ─────────────────────────────────────────────────────────────────

    @Test
    void versions_listsOriginalAndEverySignedVersion() {
        StepVerifier.create(service.versions(EMAIL, evidence.getId()))
                .assertNext(r -> {
                    assertEquals(SignedVersionService.sha256Hex(ORIGINAL), r.original().sha256());
                    assertEquals(2, r.versions().size());
                    assertEquals(SignedVersionService.sha256Hex(SIGNED_V2), r.versions().get(1).sha256());
                })
                .verifyComplete();
    }

    @Test
    void contentType_mapsKnownTypesAndFallsBackForOthers() {
        assertEquals("image/jpeg", DocumentIntegrityService.contentType("JPG"));
        assertEquals("application/octet-stream", DocumentIntegrityService.contentType("zip"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private EvidenceDocumentVersion version(int no, int signatureCount, byte[] content) {
        EvidenceDocumentVersion v = new EvidenceDocumentVersion();
        v.setId(UUID.randomUUID());
        v.setEvidenceId(evidence.getId());
        v.setVersionNo(no);
        v.setSignatureCount(signatureCount);
        v.setSha256(SignedVersionService.sha256Hex(content));
        v.setSizeBytes((long) content.length);
        v.setCreatedAt(LocalDateTime.now());
        return v;
    }

    private EvidenceSignature signature(String name) {
        EvidenceSignature s = new EvidenceSignature();
        s.setId(UUID.randomUUID());
        s.setSignerName(name);
        s.setSignedAt(LocalDateTime.now());
        return s;
    }
}
