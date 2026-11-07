package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.StoragePrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/storage")
@Tag(name = "Admin Storage", description = "Platform-admin maintenance of stored evidence files")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStorageController {

    private final StoragePrivacyService storagePrivacyService;
    private final AdminAuditService adminAuditService;
    private final UserRepository userRepository;

    public AdminStorageController(StoragePrivacyService storagePrivacyService,
                                  AdminAuditService adminAuditService,
                                  UserRepository userRepository) {
        this.storagePrivacyService = storagePrivacyService;
        this.adminAuditService = adminAuditService;
        this.userRepository = userRepository;
    }

    @PostMapping("/make-private")
    @Operation(summary = "Make all stored evidence files private",
            description = "Moves every stored original, Word/Excel PDF conversion and signed version from public to " +
                    "private delivery, so files can only be fetched through the API. Defaults to a dry run that " +
                    "only counts; pass dryRun=false to switch them. Safe to run again: files already private are " +
                    "skipped and failures are retried. Real runs are recorded in the admin audit log.")
    public Mono<StoragePrivacyService.Report> makePrivate(
            Authentication authentication,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return storagePrivacyService.makeAllPrivate(dryRun)
                .flatMap(report -> dryRun
                        ? Mono.just(report)
                        : userRepository.findByUsername(authentication.getName())
                                .switchIfEmpty(Mono.error(new InvalidRecord("Admin user not found")))
                                .flatMap(admin -> adminAuditService.record(admin.getId(), "MAKE_EVIDENCE_FILES_PRIVATE",
                                        "STORAGE", "evidence-files", null, report))
                                .thenReturn(report));
    }
}
