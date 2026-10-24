package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminSeederTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private SuperAdminSeeder seeder() {
        return new SuperAdminSeeder(userRepository, passwordEncoder);
    }

    private SuperAdminSeeder configured(String username, String password) {
        SuperAdminSeeder seeder = seeder();
        ReflectionTestUtils.setField(seeder, "adminUsername", username);
        ReflectionTestUtils.setField(seeder, "adminPassword", password);
        return seeder;
    }

    @Test
    void run_adminDoesNotExist_seedsUserWithConfiguredUsernameNotHardcodedOne() {
        SuperAdminSeeder seeder = configured("info@auditinsight.tech", "Secret123");
        when(userRepository.existsByUsername("info@auditinsight.tech")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(Mono.just(new User()));

        seeder.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("info@auditinsight.tech");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isVerified()).isTrue();
        assertThat(saved.getPassword()).isEqualTo("encoded");
    }

    @Test
    void run_adminAlreadyExists_doesNotSaveAgain() {
        SuperAdminSeeder seeder = configured("info@auditinsight.tech", "Secret123");
        when(userRepository.existsByUsername("info@auditinsight.tech")).thenReturn(Mono.just(true));

        seeder.run();

        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void run_blankAdminUsername_throwsIllegalStateException() {
        SuperAdminSeeder seeder = configured("  ", "Secret123");

        assertThatThrownBy(seeder::run).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void run_blankAdminPassword_throwsIllegalStateException() {
        SuperAdminSeeder seeder = configured("info@auditinsight.tech", "");

        assertThatThrownBy(seeder::run).isInstanceOf(IllegalStateException.class);
    }
}
