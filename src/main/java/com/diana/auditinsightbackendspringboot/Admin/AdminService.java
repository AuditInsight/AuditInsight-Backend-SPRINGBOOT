package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.ResponseMessage;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public AdminService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public Mono<ResponseMessage> activateAccount(String email) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("No account found for: " + email)))
                .flatMap(user -> {
                    if (user.isActive()) {
                        return Mono.just(new ResponseMessage(HttpStatus.OK, "Account is already active."));
                    }
                    user.setActive(true);
                    return userRepository.save(user)
                            .then(Mono.fromRunnable(() -> emailService.sendAccountActivatedEmail(
                                            user.getUsername(), user.getFullName()))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Account activated successfully."));
                });
    }

    public Mono<ResponseMessage> deactivateAccount(String email) {
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
                            .then(Mono.fromRunnable(() -> emailService.sendAccountDeactivatedEmail(
                                            user.getUsername(), user.getFullName()))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Account deactivated successfully."));
                });
    }
}
