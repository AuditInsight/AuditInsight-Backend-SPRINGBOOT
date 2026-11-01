package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.PlatformAccessSettings;
import com.diana.auditinsightbackendspringboot.Repositories.PlatformAccessSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformFreeAccessServiceTest {

    @Mock private PlatformAccessSettingsRepository settingsRepository;

    @Test
    void isEnabled_returnsTrueWhileGlobalFreePeriodHasNotExpired() {
        PlatformAccessSettings settings = new PlatformAccessSettings();
        settings.setFreeAccessEndsAt(LocalDateTime.now().plusDays(1));
        when(settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)).thenReturn(Mono.just(settings));

        StepVerifier.create(new PlatformFreeAccessService(settingsRepository).isEnabled())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isEnabled_returnsFalseWhenFreePeriodIsExpired() {
        PlatformAccessSettings settings = new PlatformAccessSettings();
        settings.setFreeAccessEndsAt(LocalDateTime.now().minusSeconds(1));
        when(settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)).thenReturn(Mono.just(settings));

        StepVerifier.create(new PlatformFreeAccessService(settingsRepository).isEnabled())
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isEnabled_returnsFalseWhenSettingsAreMissing() {
        when(settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(new PlatformFreeAccessService(settingsRepository).isEnabled())
                .expectNext(false)
                .verifyComplete();
    }
}
