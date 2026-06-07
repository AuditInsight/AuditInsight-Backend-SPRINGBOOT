package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.CreateReviewQueueRequest;
import com.diana.auditinsightbackendspringboot.DTOs.ResolveReviewQueueRequest;
import com.diana.auditinsightbackendspringboot.DTOs.ReviewQueueResponse;
import com.diana.auditinsightbackendspringboot.Services.ReviewQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/review-queue")
@Tag(name = "Review Queue", description = "Review queue — flag issues (Auditor) and resolve them (Client/Member)")
@SecurityRequirement(name = "bearerAuth")
public class ReviewQueueController {

    private final ReviewQueueService reviewService;

    public ReviewQueueController(ReviewQueueService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @Operation(summary = "Flag an issue",
               description = "Manually flags a transaction issue. AUDITOR only.")
    public Mono<ResponseEntity<ReviewQueueResponse>> flag(
            Authentication auth,
            @Valid @RequestBody CreateReviewQueueRequest req) {
        return reviewService.flagIssue(auth.getName(), req)
                .map(r -> new ResponseEntity<>(r, HttpStatus.CREATED));
    }

    @GetMapping
    @Operation(summary = "List review queue",
               description = "Lists all review queue items for the given organisation. All members.")
    public Flux<ReviewQueueResponse> list(
            Authentication auth,
            @RequestParam UUID organisationId) {
        return reviewService.listByOrg(organisationId, auth.getName());
    }

    @GetMapping("/{itemId}")
    @Operation(summary = "Get review queue item",
               description = "Returns a single review queue item. All members.")
    public Mono<ResponseEntity<ReviewQueueResponse>> getItem(
            Authentication auth,
            @PathVariable UUID itemId) {
        return reviewService.getItem(itemId, auth.getName())
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{itemId}/resolve")
    @Operation(summary = "Resolve an issue",
               description = "Marks a review queue item as resolved. CLIENT and MEMBER only.")
    public Mono<ResponseEntity<ReviewQueueResponse>> resolve(
            Authentication auth,
            @PathVariable UUID itemId,
            @Valid @RequestBody ResolveReviewQueueRequest req) {
        return reviewService.resolveIssue(itemId, auth.getName(), req)
                .map(ResponseEntity::ok);
    }
}
