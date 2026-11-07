package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceDocumentVersionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoragePrivacyServiceTest {

    private static final String PUBLIC_ORIGINAL = "https://res.cloudinary.com/demo/image/upload/v1/auditinsight/org/receipt.jpg";
    private static final String PRIVATE_ORIGINAL = "https://res.cloudinary.com/demo/image/authenticated/v2/auditinsight/org/receipt.jpg";
    private static final String PUBLIC_VERSION = "https://res.cloudinary.com/demo/raw/upload/v1/auditinsight/org/signed/a.pdf";
    private static final String PRIVATE_VERSION = "https://res.cloudinary.com/demo/raw/authenticated/v2/auditinsight/org/signed/a.pdf";
    private static final String ALREADY_PRIVATE = "https://res.cloudinary.com/demo/raw/authenticated/v1/auditinsight/org/x.pdf";

    @Mock private EvidenceRepository evidenceRepo;
    @Mock private EvidenceDocumentVersionRepository versionRepo;
    @Mock private CloudinaryService cloudinaryService;

    private StoragePrivacyService service;
    private Evidence publicEvidence;
    private Evidence privateEvidence;
    private EvidenceDocumentVersion publicVersion;

    @BeforeEach
    void setUp() {
        service = new StoragePrivacyService(evidenceRepo, versionRepo, cloudinaryService);
        publicEvidence = new Evidence();
        publicEvidence.setId(UUID.randomUUID());
        publicEvidence.setFileUpload(PUBLIC_ORIGINAL);
        privateEvidence = new Evidence();
        privateEvidence.setId(UUID.randomUUID());
        privateEvidence.setFileUpload(ALREADY_PRIVATE);
        publicVersion = new EvidenceDocumentVersion();
        publicVersion.setEvidenceId(publicEvidence.getId());
        publicVersion.setVersionNo(1);
        publicVersion.setFileUrl(PUBLIC_VERSION);
        when(evidenceRepo.findAll()).thenReturn(Flux.just(publicEvidence, privateEvidence));
        when(versionRepo.findAll()).thenReturn(Flux.just(publicVersion));
        lenient().when(evidenceRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(versionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void dryRun_onlyCountsAndChangesNothing() {
        StepVerifier.create(service.makeAllPrivate(true))
                .assertNext(r -> {
                    assertTrue(r.dryRun());
                    assertEquals(3, r.filesChecked());
                    assertEquals(1, r.alreadyPrivate());
                    assertEquals(2, r.toMakePrivate());
                    assertEquals(0, r.madePrivate());
                })
                .verifyComplete();
        verify(cloudinaryService, never()).makePrivate(any());
        verify(evidenceRepo, never()).save(any());
        verify(versionRepo, never()).save(any());
    }

    @Test
    void run_switchesPublicFilesAndStoresTheirNewUrls() {
        when(cloudinaryService.makePrivate(PUBLIC_ORIGINAL)).thenReturn(Mono.just(PRIVATE_ORIGINAL));
        when(cloudinaryService.makePrivate(PUBLIC_VERSION)).thenReturn(Mono.just(PRIVATE_VERSION));

        StepVerifier.create(service.makeAllPrivate(false))
                .assertNext(r -> {
                    assertEquals(2, r.madePrivate());
                    assertTrue(r.failures().isEmpty());
                })
                .verifyComplete();
        assertEquals(PRIVATE_ORIGINAL, publicEvidence.getFileUpload());
        assertEquals(PRIVATE_VERSION, publicVersion.getFileUrl());
        verify(evidenceRepo).save(publicEvidence);
        verify(evidenceRepo, never()).save(privateEvidence); // already private: untouched
        verify(versionRepo).save(publicVersion);
    }

    @Test
    void run_aFailureIsReportedAndTheRestStillSwitch() {
        when(cloudinaryService.makePrivate(PUBLIC_ORIGINAL)).thenReturn(Mono.error(new RuntimeException("rate limited")));
        when(cloudinaryService.makePrivate(PUBLIC_VERSION)).thenReturn(Mono.just(PRIVATE_VERSION));

        StepVerifier.create(service.makeAllPrivate(false))
                .assertNext(r -> {
                    assertEquals(1, r.madePrivate());
                    assertEquals(1, r.failures().size());
                    assertTrue(r.failures().get(0).contains("rate limited"));
                })
                .verifyComplete();
        assertEquals(PUBLIC_ORIGINAL, publicEvidence.getFileUpload()); // left as it was; retried on the next run
        verify(evidenceRepo, never()).save(publicEvidence);
        verify(versionRepo).save(publicVersion);
    }
}
