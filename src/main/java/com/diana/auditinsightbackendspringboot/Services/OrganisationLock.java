package com.diana.auditinsightbackendspringboot.Services;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs work for one organisation inside a database transaction while holding a per-organisation lock, so changes
 * that must see a consistent picture of the organisation's paid periods and trial (activating a payment, granting,
 * extending, shortening or revoking a trial) never interleave. Everything inside commits or rolls back together.
 * <p>
 * The lock is a PostgreSQL transaction-scoped advisory lock (released automatically at commit/rollback). On other
 * databases (the H2 test database) only the transaction is applied.
 */
@Service
public class OrganisationLock {

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final boolean postgres;

    public OrganisationLock(DatabaseClient databaseClient, ReactiveTransactionManager transactionManager,
                            ConnectionFactory connectionFactory) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.postgres = connectionFactory.getMetadata().getName().toLowerCase().contains("postgres");
    }

    public <T> Mono<T> inTransaction(UUID organisationId, Supplier<Mono<T>> work) {
        Mono<Void> lock = postgres
                ? databaseClient.sql("SELECT pg_advisory_xact_lock(hashtext(:key))")
                        .bind("key", "organisation:" + organisationId)
                        .fetch().all().then()
                : Mono.empty();
        return transactionalOperator.transactional(lock.then(Mono.defer(work)));
    }
}
