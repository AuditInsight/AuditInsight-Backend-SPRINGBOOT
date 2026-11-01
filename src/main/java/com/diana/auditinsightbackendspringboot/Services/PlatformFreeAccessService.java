package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.PlatformAccessSettings;
import com.diana.auditinsightbackendspringboot.Repositories.PlatformAccessSettingsRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class PlatformFreeAccessService {

    private final PlatformAccessSettingsRepository settingsRepository;

    public PlatformFreeAccessService(PlatformAccessSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public Mono<Boolean> isEnabled() {
        return settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)
                .map(settings -> settings.getFreeAccessEndsAt() != null
                        && settings.getFreeAccessEndsAt().isAfter(LocalDateTime.now()))
                .defaultIfEmpty(false);
    }
}
