package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.Services.TrialCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Unauthenticated: lets the landing and sign-up pages say "Start free trial" only while a campaign is running. */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Information shown before sign-in")
public class PublicOfferController {

    private final TrialCampaignService trialCampaignService;

    public PublicOfferController(TrialCampaignService trialCampaignService) {
        this.trialCampaignService = trialCampaignService;
    }

    @GetMapping("/trial-offer")
    @Operation(summary = "Current free-trial offer",
            description = "Whether new organisations currently receive a free trial, and how many days. Exposes "
                    + "nothing else about campaigns.")
    public Mono<TrialCampaignService.TrialOffer> trialOffer() {
        return trialCampaignService.currentOffer();
    }
}
