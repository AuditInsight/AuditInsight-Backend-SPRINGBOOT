package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SuperAdminSeeder implements CommandLineRunner {
    @Value("${ADMIN_USERNAME}")
    private String adminUsername;
    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public SuperAdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Boolean exists = userRepository.existsByUsername(adminUsername).block();
        if (Boolean.FALSE.equals(exists)) {
            User admin = new User();
            admin.setFullName("Super Admin");
            admin.setUsername("admin@auditinsight.com");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setAuthProvider("JWT");
            admin.setVerified(true);
            userRepository.save(admin).block();
            System.out.println("[AdminSeeder] Super admin seeded: admin@auditinsight.com");
        } else {
            System.out.println("[AdminSeeder] Super admin already exists: admin@auditinsight.com");
        }
    }
}
