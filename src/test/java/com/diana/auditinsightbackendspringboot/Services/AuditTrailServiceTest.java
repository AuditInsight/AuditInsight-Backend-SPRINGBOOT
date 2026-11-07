package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.RequestMeta;
import com.diana.auditinsightbackendspringboot.Enum.AuditEventType;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceAuditEvent;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceAuditEventRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    @Mock private EvidenceAuditEventRepository eventRepo;
    @Mock private EvidenceRepository evidenceRepo;
    @Mock private OrgAccessService orgAccessService;

    @Test
    void record_storesWhoWhatWhereAndWhichFile() {
        AuditTrailService service = new AuditTrailService(eventRepo, evidenceRepo, orgAccessService);
        when(eventRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        Evidence ev = evidence();
        User user = new User();
        user.setId(5L);
        user.setFullName("Jane Doe");

        StepVerifier.create(service.record(ev, AuditEventType.SIGNED, user, new RequestMeta("41.186.0.1", "Firefox"),
                        new AuditTrailService.Detail(2, "a".repeat(64), "Signed as Jane Doe")))
                .verifyComplete();

        ArgumentCaptor<EvidenceAuditEvent> saved = ArgumentCaptor.forClass(EvidenceAuditEvent.class);
        verify(eventRepo).save(saved.capture());
        EvidenceAuditEvent e = saved.getValue();
        assertEquals("SIGNED", e.getEventType());
        assertEquals(5L, e.getActorId());
        assertEquals("Jane Doe", e.getActorName());
        assertEquals("41.186.0.1", e.getIpAddress());
        assertEquals("Firefox", e.getUserAgent());
        assertEquals(2, e.getVersionNo());
        assertEquals(ev.getOrganisationId(), e.getOrganisationId());
        assertNotNull(e.getCreatedAt());
        assertEquals(ZoneOffset.UTC, e.getCreatedAt().getOffset());
    }

    @Test
    void record_storageFailure_neverFailsTheCaller() {
        AuditTrailService service = new AuditTrailService(eventRepo, evidenceRepo, orgAccessService);
        when(eventRepo.save(any())).thenReturn(Mono.error(new RuntimeException("DB down")));

        StepVerifier.create(service.record(evidence(), AuditEventType.ORIGINAL_ACCESSED, null, null, null))
                .verifyComplete();
    }

    @Test
    void requestMeta_readsClientAddressAndBrowser() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("197.243.1.2", 443))
                .header("User-Agent", "Mozilla/5.0")
                .build();

        RequestMeta meta = RequestMeta.from(request);

        assertEquals("197.243.1.2", meta.ip());
        assertEquals("Mozilla/5.0", meta.userAgent());
    }

    private static Evidence evidence() {
        Evidence ev = new Evidence();
        ev.setId(UUID.randomUUID());
        ev.setOrganisationId(UUID.randomUUID());
        return ev;
    }
}
