package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import com.diana.auditinsightbackendspringboot.Models.Plan;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PlanRepository extends ReactiveCrudRepository<Plan, PlanTier> {
}
