package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.PlatformAccessSettings;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PlatformAccessSettingsRepository extends ReactiveCrudRepository<PlatformAccessSettings, String> {
}
