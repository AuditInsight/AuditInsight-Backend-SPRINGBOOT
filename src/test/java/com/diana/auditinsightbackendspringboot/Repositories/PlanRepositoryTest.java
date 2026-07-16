package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
class PlanRepositoryTest {

    @Autowired
    private PlanRepository planRepository;

    @Test
    void allFourSeededPlansAreReadable() {
        StepVerifier.create(planRepository.findAll())
                .expectNextCount(4)
                .verifyComplete();
    }

    @Test
    void enterprisePlanHasUnlimitedUsersAndAllFeatures() {
        StepVerifier.create(planRepository.findById(PlanTier.ENTERPRISE))
                .expectNextMatches(plan -> plan.getMaxUsers() == null
                        && plan.getAuditsPerMonth() == null
                        && plan.isSlaGuarantee()
                        && plan.isSsoSaml())
                .verifyComplete();
    }

    @Test
    void freePlanHasBasicFeaturesOnly() {
        StepVerifier.create(planRepository.findById(PlanTier.FREE))
                .expectNextMatches(plan -> plan.getMaxUsers() == 2
                        && plan.getAuditsPerMonth() == 5
                        && plan.isBasicReports()
                        && !plan.isAdvancedReports())
                .verifyComplete();
    }
}
