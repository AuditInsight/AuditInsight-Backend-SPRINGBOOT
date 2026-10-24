package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminPlanPriceResponse;
import com.diana.auditinsightbackendspringboot.DTOs.AdminUpdatePlanPriceRequest;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.PlanPriceRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class AdminPlanService {

    private final PlanPriceRepository planPriceRepository;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    public AdminPlanService(PlanPriceRepository planPriceRepository, UserRepository userRepository,
                             AdminAuditService adminAuditService) {
        this.planPriceRepository = planPriceRepository;
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
    }

    public Flux<AdminPlanPriceResponse> listPlans() {
        return planPriceRepository.findAllByOrderBySubscriptionType().flatMap(this::toResponse);
    }

    public Mono<AdminPlanPriceResponse> updatePrice(SubscriptionType type, AdminUpdatePlanPriceRequest request,
                                                     String actingAdminUsername) {
        return planPriceRepository.findById(type)
                .switchIfEmpty(Mono.error(new InvalidRecord("Unknown subscription type: " + type)))
                .flatMap(existing -> toResponse(existing)
                        .flatMap(before -> userRepository.findByUsername(actingAdminUsername)
                                .switchIfEmpty(Mono.error(new InvalidRecord("Admin user not found")))
                                .flatMap(admin -> {
                                    existing.setPrice(request.getPrice());
                                    existing.setDurationDays(request.getDurationDays());
                                    existing.setUpdatedBy(admin.getId());
                                    existing.setUpdatedAt(LocalDateTime.now());
                                    return planPriceRepository.save(existing)
                                            .flatMap(saved -> toResponse(saved)
                                                    .flatMap(after -> adminAuditService.record(admin.getId(), "UPDATE_PLAN_PRICE",
                                                                    "PLAN_PRICE", type.name(), before, after)
                                                            .thenReturn(after)));
                                })));
    }

    private Mono<AdminPlanPriceResponse> toResponse(PlanPrice planPrice) {
        if (planPrice.getUpdatedBy() == null) {
            return Mono.just(new AdminPlanPriceResponse(planPrice.getSubscriptionType(), planPrice.getPrice(),
                    planPrice.getCurrency(), planPrice.getDurationDays(), null, planPrice.getUpdatedAt()));
        }
        return userRepository.findById(planPrice.getUpdatedBy())
                .map(User::getUsername)
                .defaultIfEmpty("Unknown admin")
                .map(username -> new AdminPlanPriceResponse(planPrice.getSubscriptionType(), planPrice.getPrice(),
                        planPrice.getCurrency(), planPrice.getDurationDays(), username, planPrice.getUpdatedAt()));
    }
}
