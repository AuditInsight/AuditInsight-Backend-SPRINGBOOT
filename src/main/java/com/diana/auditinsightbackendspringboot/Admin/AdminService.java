package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.ResponseMessage;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import com.diana.auditinsightbackendspringboot.Repositories.AuditorRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AuditorRepository auditorRepository;
    private final EmailService emailService;

    public AdminService(UserRepository userRepository, AuditorRepository auditorRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.auditorRepository = auditorRepository;
        this.emailService = emailService;
    }

    public Flux<AuditorProfile> getPendingAuditors() {
        return userRepository.findAllByRoleAndVerified(Role.AUDITOR, false)
                .flatMap(user -> auditorRepository.findByEmailAddress(user.getUsername()));
    }

    public Mono<ResponseMessage> approveAuditor(String email) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("No account found for: " + email)))
                .flatMap(user -> {
                    if (user.getRole() != Role.AUDITOR) {
                        return Mono.error(new InvalidRecord("Account is not an AUDITOR."));
                    }
                    if (user.isVerified()) {
                        return Mono.just(new ResponseMessage(HttpStatus.OK, "Account is already approved."));
                    }
                    user.setVerified(true);
                    return userRepository.save(user)
                            .then(auditorRepository.findByEmailAddress(email)
                                    .flatMap(profile ->
                                            Mono.fromRunnable(() -> emailService.sendApprovalEmail(
                                                            profile.getEmailAddress(), profile.getFirstName()))
                                                    .subscribeOn(Schedulers.boundedElastic()))
                                    .then())
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Auditor account approved successfully."));
                });
    }
}
