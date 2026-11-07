package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceDocumentVersionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.EvidenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One-off (and safely repeatable) job that moves every stored evidence file from public to private delivery in
 * Cloudinary: original uploads, Word/Excel PDF conversions and stored signed versions. Each file keeps its bytes,
 * so recorded SHA-256 fingerprints stay valid; only the stored URL changes. Files are processed one at a time; a
 * file that can't be switched is reported and left as it was, and running the job again retries it.
 */
@Service
@Slf4j
public class StoragePrivacyService {

    /** What the job found or did. In a dry run, {@code toMakePrivate} counts what a real run would switch. */
    public record Report(boolean dryRun, int filesChecked, int alreadyPrivate, int toMakePrivate, int madePrivate,
                         List<String> failures) {}

    private final EvidenceRepository evidenceRepo;
    private final EvidenceDocumentVersionRepository versionRepo;
    private final CloudinaryService cloudinaryService;

    public StoragePrivacyService(EvidenceRepository evidenceRepo,
                                 EvidenceDocumentVersionRepository versionRepo,
                                 CloudinaryService cloudinaryService) {
        this.evidenceRepo = evidenceRepo;
        this.versionRepo = versionRepo;
        this.cloudinaryService = cloudinaryService;
    }

    private static final class Tally {
        final AtomicInteger checked = new AtomicInteger();
        final AtomicInteger alreadyPrivate = new AtomicInteger();
        final AtomicInteger toMakePrivate = new AtomicInteger();
        final AtomicInteger madePrivate = new AtomicInteger();
        final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    }

    public Mono<Report> makeAllPrivate(boolean dryRun) {
        Tally tally = new Tally();
        Mono<Void> evidence = evidenceRepo.findAll()
                .concatMap(ev -> processEvidence(ev, dryRun, tally))
                .then();
        Mono<Void> versions = versionRepo.findAll()
                .concatMap(v -> processVersion(v, dryRun, tally))
                .then();
        return evidence.then(versions).then(Mono.fromSupplier(() -> {
            Report report = new Report(dryRun, tally.checked.get(), tally.alreadyPrivate.get(),
                    tally.toMakePrivate.get(), tally.madePrivate.get(), List.copyOf(tally.failures));
            log.info("Storage privacy {}: {}", dryRun ? "dry run" : "run", report);
            return report;
        }));
    }

    private Mono<Void> processEvidence(Evidence ev, boolean dryRun, Tally tally) {
        return switchUrl(ev.getFileUpload(), "evidence " + ev.getId() + " original", dryRun, tally, ev::setFileUpload)
                .zipWith(switchUrl(ev.getPdfRenditionUrl(), "evidence " + ev.getId() + " PDF conversion", dryRun, tally,
                        ev::setPdfRenditionUrl))
                .flatMap(changed -> changed.getT1() || changed.getT2() ? evidenceRepo.save(ev).then() : Mono.empty());
    }

    private Mono<Void> processVersion(EvidenceDocumentVersion v, boolean dryRun, Tally tally) {
        return switchUrl(v.getFileUrl(), "evidence " + v.getEvidenceId() + " signed v" + v.getVersionNo(), dryRun,
                tally, v::setFileUrl)
                .flatMap(changed -> changed ? versionRepo.save(v).then() : Mono.empty());
    }

    /** Emits true when the URL was switched (and passed to {@code setter}), false otherwise. Never errors. */
    private Mono<Boolean> switchUrl(String url, String label, boolean dryRun, Tally tally, Consumer<String> setter) {
        if (url == null || url.isBlank()) return Mono.just(false);
        tally.checked.incrementAndGet();
        if (CloudinaryService.isPrivateUrl(url)) {
            tally.alreadyPrivate.incrementAndGet();
            return Mono.just(false);
        }
        tally.toMakePrivate.incrementAndGet();
        if (dryRun) return Mono.just(false);
        return cloudinaryService.makePrivate(url)
                .map(newUrl -> {
                    setter.accept(newUrl);
                    tally.madePrivate.incrementAndGet();
                    return true;
                })
                .onErrorResume(e -> {
                    log.warn("Could not make {} private: {}", label, e.getMessage());
                    tally.failures.add(label + ": " + e.getMessage());
                    return Mono.just(false);
                });
    }
}
