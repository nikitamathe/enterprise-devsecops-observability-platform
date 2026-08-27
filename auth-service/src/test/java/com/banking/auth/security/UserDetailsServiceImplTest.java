package com.banking.auth.security;

import com.banking.auth.model.User;
import com.banking.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userRepository);
    }

    private User userWithRoleAndEnabled(User.Role role, boolean enabled) {
        return User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .password("hashed")
                .role(role)
                .enabled(enabled)
                .build();
    }

    @Test
    void loadsUserWithRoleAuthority() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(userWithRoleAndEnabled(User.Role.USER, true)));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    void loadsAdminRoleAuthority() {
        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(userWithRoleAndEnabled(User.Role.ADMIN, true)));

        UserDetails details = service.loadUserByUsername("bob");

        assertThat(details.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void marksDisabledUserAsDisabled() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(userWithRoleAndEnabled(User.Role.USER, false)));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void throwsWhenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}