package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.UpdateClientProfileRequest;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import com.diana.auditinsightbackendspringboot.Repositories.ClientRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ClientProfileService {

    private final ClientRepository clientRepository;

    public ClientProfileService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Mono<ClientProfile> getProfile(String email) {
        return clientRepository.findByEmailAddress(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("Client profile not found.")));
    }

    public Mono<ClientProfile> updateProfile(String email, UpdateClientProfileRequest request) {
        return clientRepository.findByEmailAddress(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("Client profile not found.")))
                .flatMap(profile -> {
                    if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
                    if (request.getLastName() != null) profile.setLastName(request.getLastName());
                    profile.setPhone(request.getPhone());
                    profile.setAddress(request.getAddress());
                    return clientRepository.save(profile);
                });
    }
}
