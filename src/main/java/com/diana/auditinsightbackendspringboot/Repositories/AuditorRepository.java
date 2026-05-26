package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import jakarta.validation.constraints.Email;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditorRepository extends JpaRepository<AuditorProfile, Long> {
    Optional<AuditorProfile> findByEmailAddress(@Email(message = "the email must be valid", regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$ ") String username);
}