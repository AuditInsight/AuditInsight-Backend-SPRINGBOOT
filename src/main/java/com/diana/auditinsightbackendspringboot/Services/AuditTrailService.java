package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.AuditEventResponse;
import com.diana.auditinsightbackendspringboot.DTOs.RequestMeta;
import com.diana.auditinsightbackendspringboot.Enum.AuditEventType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceAuditEvent;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceAuditEventRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Append-only activity log for evidence documents: uploads, signature requests, signatures, stored versions,
 * file access and verification checks, each with who, when (UTC), from where, and which exact file (SHA-256).
 */
@Service
@Slf4j
public class AuditTrailService {

    /** Optional extras for an event. */
    public record Detail(Integer versionNo, String sha256, String text) {
        public static final Detail NONE = new Detail(null, null, null);
        public static Detail text(String text) { return new Detail(null, null, text); }
    }

    private final EvidenceAuditEventRepository eventRepo;
    private final EvidenceRepository evidenceRepo;
    private final OrgAccessService orgAccessService;

    public AuditTrailService(EvidenceAuditEventRepository eventRepo,
                             EvidenceRepository evidenceRepo,
                             OrgAccessService orgAccessService) {
        this.eventRepo = eventRepo;
        this.evidenceRepo = evidenceRepo;
        this.orgAccessService = orgAccessService;
    }

    /**
     * Records an event. Never fails the caller: a logging problem is reported in the server log, but the
     * action itself (signing, downloading, ...) has already happened and must not be undone or blocked by it.
     */
    public Mono<Void> record(Evidence ev, AuditEventType type, User actor, RequestMeta meta, Detail detail) {
        return Mono.defer(() -> {
            EvidenceAuditEvent e = new EvidenceAuditEvent();
            e.setEvidenceId(ev.getId());
            e.setOrganisationId(ev.getOrganisationId());
            e.setEventType(type.name());
            if (actor != null) {
                e.setActorId(actor.getId());
                e.setActorName(actor.getFullName());
            }
            RequestMeta m = meta == null ? RequestMeta.NONE : meta;
            e.setIpAddress(m.ip());
            e.setUserAgent(m.userAgent());
            Detail d = detail == null ? Detail.NONE : detail;
            e.setVersionNo(d.versionNo());
            e.setSha256(d.sha256());
            e.setDetails(d.text());
            e.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return eventRepo.save(e).then();
        }).onErrorResume(err -> {
            log.error("Could not record {} audit event for evidence {}: {}", type, ev.getId(), err.getMessage());
            return Mono.empty();
        });
    }

    /** The document's activity, oldest first. Any member of the evidence's organisation may read it. */
    public Flux<AuditEventResponse> list(String email, UUID evidenceId) {
        return evidenceRepo.findById(evidenceId)
                .switchIfEmpty(Mono.error(new InvalidRecord("Evidence not found")))
                .flatMap(ev -> orgAccessService.resolveGatedMember(ev.getOrganisationId(), email))
                .thenMany(Flux.defer(() -> eventRepo.findAllByEvidenceIdOrderByCreatedAtAsc(evidenceId)))
                .map(e -> new AuditEventResponse(e.getId(), e.getEventType(), e.getActorName(), e.getVersionNo(),
                        e.getSha256(), e.getIpAddress(), e.getUserAgent(), e.getDetails(), e.getCreatedAt()));
    }
}
