package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.UpdateAuditorProfileRequest;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import com.diana.auditinsightbackendspringboot.Repositories.AuditorRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuditorProfileService {

    private final AuditorRepository auditorRepository;

    public AuditorProfileService(AuditorRepository auditorRepository) {
        this.auditorRepository = auditorRepository;
    }

    public Mono<AuditorProfile> getProfile(String email) {
        return auditorRepository.findByEmailAddress(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("Auditor profile not found.")));
    }

    public Mono<AuditorProfile> updateProfile(String email, UpdateAuditorProfileRequest request) {
        return auditorRepository.findByEmailAddress(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("Auditor profile not found.")))
                .flatMap(profile -> {
                    if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
                    if (request.getLastName() != null) profile.setLastName(request.getLastName());
                    if (request.getPhone() != null) profile.setPhone(request.getPhone());
                    if (request.getCertificationNumber() != null)
                        profile.setCertificationNumber(request.getCertificationNumber());
                    return auditorRepository.save(profile);
                });
    }
}
