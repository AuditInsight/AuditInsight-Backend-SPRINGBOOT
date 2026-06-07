package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.EvidenceStatus;
import com.diana.auditinsightbackendspringboot.Enum.TransactionStatus;
import com.diana.auditinsightbackendspringboot.Repositories.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Component
@Slf4j
public class ReviewQueueScheduler {

    private final TransactionRepository txnRepo;
    private final TransactionService txnService;

    public ReviewQueueScheduler(TransactionRepository txnRepo, TransactionService txnService) {
        this.txnRepo = txnRepo;
        this.txnService = txnService;
    }

    @Scheduled(fixedRate = 3_600_000) // every hour
    public void flagStaleTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        txnRepo.findByStatusAndEvidenceStatusAndCreatedAtBefore(
                        TransactionStatus.PENDING, EvidenceStatus.MISSING, cutoff)
                .flatMap(t -> txnService.createSystemFlag(t.getOrganisationId(), t.getId()))
                .doOnError(e -> log.error("Auto-flag error: {}", e.getMessage()))
                .subscribe();
    }
}
