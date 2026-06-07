package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CustomUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository repository;

    public CustomUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @NullMarked
    public Mono<UserDetails> findByUsername(String username) {
        return repository.findByUsername(username)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(username + " not found")))
                .map(user -> org.springframework.security.core.userdetails.User
                        .builder()
                        .username(user.getUsername())
                        .password(user.getPassword() != null ? user.getPassword() : "")
                        .roles(user.getRole().name())
                        .build()
                );
    }
}
