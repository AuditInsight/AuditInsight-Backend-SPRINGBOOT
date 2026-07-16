package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.Plan;
import com.diana.auditinsightbackendspringboot.Repositories.PlanRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public Flux<Plan> listPlans() {
        return planRepository.findAll();
    }
}
