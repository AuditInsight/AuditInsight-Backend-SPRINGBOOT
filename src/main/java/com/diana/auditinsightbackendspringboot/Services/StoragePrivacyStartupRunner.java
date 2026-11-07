package com.diana.auditinsightbackendspringboot.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Runs the "make stored files private" job once at startup when STORAGE_MAKE_PRIVATE is set, so it can be
 * triggered from the hosting settings without an admin login:
 * <ul>
 *   <li>{@code dry-run} — only counts what would be switched;</li>
 *   <li>{@code run} — switches every public stored file to private;</li>
 *   <li>empty (default) — does nothing.</li>
 * </ul>
 * The job runs in the background (startup isn't delayed) and writes its report to the log, prefixed with
 * "[storage-privacy]". Remove the setting afterwards; the job is safe to repeat but has no reason to.
 */
@Component
@Slf4j
public class StoragePrivacyStartupRunner implements ApplicationRunner {

    private static final String TAG = "[storage-privacy]";

    private final StoragePrivacyService storagePrivacyService;
    private final String mode;

    public StoragePrivacyStartupRunner(StoragePrivacyService storagePrivacyService,
                                       @Value("${storage.make-private-on-startup:}") String mode) {
        this.storagePrivacyService = storagePrivacyService;
        this.mode = mode == null ? "" : mode.trim().toLowerCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        trigger().subscribe();
    }

    /** The background job for the configured mode; empty when the setting is off or unrecognised. */
    Mono<StoragePrivacyService.Report> trigger() {
        boolean dryRun;
        switch (mode) {
            case "" -> { return Mono.empty(); }
            case "dry-run", "dryrun" -> dryRun = true;
            case "run" -> dryRun = false;
            default -> {
                log.warn("{} ignoring unknown STORAGE_MAKE_PRIVATE value '{}' (use dry-run or run)", TAG, mode);
                return Mono.empty();
            }
        }
        log.info("{} starting {}", TAG, dryRun ? "DRY RUN (nothing will change)" : "RUN (switching files to private)");
        return storagePrivacyService.makeAllPrivate(dryRun)
                .doOnNext(r -> {
                    log.info("{} finished {}: checked={} alreadyPrivate={} toMakePrivate={} madePrivate={} failures={}",
                            TAG, r.dryRun() ? "DRY RUN" : "RUN", r.filesChecked(), r.alreadyPrivate(),
                            r.toMakePrivate(), r.madePrivate(), r.failures().size());
                    r.failures().forEach(f -> log.warn("{} failed: {}", TAG, f));
                })
                .doOnError(e -> log.error("{} job failed: {}", TAG, e.getMessage(), e))
                .onErrorResume(e -> Mono.empty())
                .subscribeOn(Schedulers.boundedElastic());
    }
}
