package com.diana.auditinsightbackendspringboot.Repositories;


import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Models.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    Mono<User> findByUsername(String username);

    Mono<Boolean> existsByUsername(String username);

    /** Admin user list — {@code role}/{@code search} are nullable filters (search matches username or full name). */
    @Query("SELECT * FROM users " +
            "WHERE (:role IS NULL OR role = :role) " +
            "AND (:search IS NULL OR LOWER(username) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(full_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<User> findPage(Role role, String search, int limit, long offset);

    @Query("SELECT COUNT(*) FROM users " +
            "WHERE (:role IS NULL OR role = :role) " +
            "AND (:search IS NULL OR LOWER(username) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(full_name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Mono<Long> countPage(Role role, String search);
}
