package com.diana.auditinsightbackendspringboot.Services;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoragePrivacyStartupRunnerTest {

    private final StoragePrivacyService service = mock(StoragePrivacyService.class);

    private static StoragePrivacyService.Report report(boolean dryRun) {
        return new StoragePrivacyService.Report(dryRun, 3, 1, 2, dryRun ? 0 : 2, List.of());
    }

    @Test
    void off_byDefault() {
        StepVerifier.create(new StoragePrivacyStartupRunner(service, "").trigger()).verifyComplete();
        verify(service, never()).makeAllPrivate(anyBoolean());
    }

    @Test
    void dryRun_onlyCounts() {
        when(service.makeAllPrivate(true)).thenReturn(Mono.just(report(true)));
        StepVerifier.create(new StoragePrivacyStartupRunner(service, " Dry-Run ").trigger())
                .expectNextCount(1).verifyComplete();
        verify(service, never()).makeAllPrivate(false);
    }

    @Test
    void run_switchesFiles() {
        when(service.makeAllPrivate(false)).thenReturn(Mono.just(report(false)));
        StepVerifier.create(new StoragePrivacyStartupRunner(service, "run").trigger())
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void unknownValue_doesNothing() {
        StepVerifier.create(new StoragePrivacyStartupRunner(service, "yes").trigger()).verifyComplete();
        verify(service, never()).makeAllPrivate(anyBoolean());
    }

    @Test
    void jobFailure_neverBreaksStartup() {
        when(service.makeAllPrivate(true)).thenReturn(Mono.error(new RuntimeException("DB down")));
        StepVerifier.create(new StoragePrivacyStartupRunner(service, "dry-run").trigger()).verifyComplete();
    }
}
