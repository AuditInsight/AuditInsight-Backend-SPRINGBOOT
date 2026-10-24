package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.ResponseMessage;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AdminAuditService adminAuditService;

    public AdminService(UserRepository userRepository, EmailService emailService, AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.adminAuditService = adminAuditService;
    }

    public Mono<ResponseMessage> activateAccount(String email, String actingAdminUsername) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("No account found for: " + email)))
                .flatMap(user -> {
                    if (user.isActive()) {
                        return Mono.just(new ResponseMessage(HttpStatus.OK, "Account is already active."));
                    }
                    user.setActive(true);
                    return userRepository.save(user)
                            .flatMap(saved -> auditAndNotify(saved, actingAdminUsername, "ACTIVATE_USER", false, true)
                                    .thenReturn(saved))
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Account activated successfully."));
                });
    }

    public Mono<ResponseMessage> deactivateAccount(String email, String actingAdminUsername) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("No account found for: " + email)))
                .flatMap(user -> {
                    if (user.getRole() == Role.ADMIN) {
                        return Mono.error(new InvalidRecord("Admin accounts cannot be deactivated."));
                    }
                    if (!user.isActive()) {
                        return Mono.just(new ResponseMessage(HttpStatus.OK, "Account is already inactive."));
                    }
                    user.setActive(false);
                    return userRepository.save(user)
                            .flatMap(saved -> auditAndNotify(saved, actingAdminUsername, "DEACTIVATE_USER", true, false)
                                    .thenReturn(saved))
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Account deactivated successfully."));
                });
    }

    private Mono<Void> auditAndNotify(User user, String actingAdminUsername, String action,
                                       boolean wasActive, boolean isActive) {
        Mono<Void> notify = Mono.fromRunnable(() -> {
                    if (isActive) {
                        emailService.sendAccountActivatedEmail(user.getUsername(), user.getFullName());
                    } else {
                        emailService.sendAccountDeactivatedEmail(user.getUsername(), user.getFullName());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();

        return userRepository.findByUsername(actingAdminUsername)
                .flatMap(admin -> adminAuditService.record(admin.getId(), action, "USER", String.valueOf(user.getId()),
                        Map.of("active", wasActive), Map.of("active", isActive)))
                .then(notify);
    }
}
