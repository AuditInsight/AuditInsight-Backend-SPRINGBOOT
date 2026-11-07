package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Word/Excel documents once a converter is configured: they are signed on the pages of their PDF conversion, and
 * any certificate issued before the converter existed stays in the history but is never built upon.
 */
@ExtendWith(MockitoExtension.class)
class SignedVersionWordSigningTest {

    @Mock private EvidenceRepository evidenceRepo;
    @Mock private EvidenceSignatureRepository signatureRepo;
    @Mock private EvidenceDocumentVersionRepository versionRepo;
    @Mock private SignedDocumentService signedDocumentService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private OrgAccessService orgAccessService;
    @Mock private AuditTrailService auditTrail;
    @Mock private DocumentConversionService conversionService;

    private SignedVersionService service;
    private Evidence evidence;
    private static final String EMAIL = "signer@test.com";
    private static final byte[] CONVERTED = "converted-word-pdf".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        when(conversionService.isEnabled()).thenReturn(true);
        service = new SignedVersionService(evidenceRepo, signatureRepo, versionRepo, signedDocumentService,
                cloudinaryService, orgAccessService, auditTrail, new SigningModeService(conversionService));
        evidence = new Evidence();
        evidence.setId(UUID.randomUUID());
        evidence.setOrganisationId(UUID.randomUUID());
        evidence.setDocumentName("Board resolution");
        evidence.setFileType("docx");
        User user = new User();
        user.setId(3L);
        lenient().when(evidenceRepo.findById(evidence.getId())).thenReturn(Mono.just(evidence));
        lenient().when(orgAccessService.resolveGatedMember(evidence.getOrganisationId(), EMAIL))
                .thenReturn(Mono.just(new OrgAccessService.OrgMemberContext(user, new OrganisationMember())));
        lenient().when(auditTrail.record(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(versionRepo.save(any())).thenAnswer(inv -> {
            EvidenceDocumentVersion v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return Mono.just(v);
        });
    }

    @Test
    void signingBase_unsignedWordDocument_isItsPdfConversion() {
        when(signatureRepo.countByEvidenceId(evidence.getId())).thenReturn(Mono.just(0L));
        when(signedDocumentService.baseDocument(evidence)).thenReturn(Mono.just(CONVERTED));

        StepVerifier.create(service.signingBase(EMAIL, evidence.getId()))
                .assertNext(base -> assertEquals(SignedVersionService.sha256Hex(CONVERTED), base.sha256()))
                .verifyComplete();
    }

    @Test
    void serve_wordDocumentWithOldCertificate_isNowSignedOnItsPagesNotOnTheCertificate() {
        byte[] stamped = "conversion-with-signatures".getBytes(StandardCharsets.UTF_8);
        // Two signatures; v1 was a certificate issued before the converter existed. There is no DOCUMENT version yet.
        when(signatureRepo.countByEvidenceId(evidence.getId())).thenReturn(Mono.just(2L));
        when(versionRepo.findFirstByEvidenceIdAndRenditionOrderByVersionNoDesc(evidence.getId(),
                EvidenceDocumentVersion.RENDITION_DOCUMENT)).thenReturn(Mono.empty());
        when(versionRepo.findFirstByEvidenceIdOrderByVersionNoDesc(evidence.getId()))
                .thenReturn(Mono.just(certificateV1()));
        when(signedDocumentService.baseDocument(evidence)).thenReturn(Mono.just(CONVERTED));
        when(signedDocumentService.stampOnto(evidence, CONVERTED, 0)).thenReturn(Mono.just(
                new SignedDocumentService.SignedDocument(stamped, "Board resolution-signed.pdf", "application/pdf", 2, false)));
        when(cloudinaryService.upload(stamped, evidence.getOrganisationId() + "/signed", "pdf"))
                .thenReturn(Mono.just("https://res.cloudinary.com/demo/raw/upload/v1/signed/v2.pdf"));

        StepVerifier.create(service.serve(EMAIL, evidence.getId()))
                .assertNext(served -> assertEquals(2, served.versionNo()))
                .verifyComplete();

        ArgumentCaptor<EvidenceDocumentVersion> saved = ArgumentCaptor.forClass(EvidenceDocumentVersion.class);
        verify(versionRepo).save(saved.capture());
        assertEquals(EvidenceDocumentVersion.RENDITION_DOCUMENT, saved.getValue().getRendition());
        verify(signedDocumentService, never()).renderSigned(any());
        verify(signedDocumentService).stampOnto(evidence, CONVERTED, 0); // built on the conversion, not on v1
    }

    private EvidenceDocumentVersion certificateV1() {
        EvidenceDocumentVersion v = new EvidenceDocumentVersion();
        v.setId(UUID.randomUUID());
        v.setEvidenceId(evidence.getId());
        v.setVersionNo(1);
        v.setSignatureCount(1);
        v.setRendition(EvidenceDocumentVersion.RENDITION_CERTIFICATE);
        v.setSha256(SignedVersionService.sha256Hex("certificate".getBytes(StandardCharsets.UTF_8)));
        return v;
    }
}
