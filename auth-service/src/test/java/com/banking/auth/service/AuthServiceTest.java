package com.banking.auth.service;

import com.banking.auth.dto.AuthResponse;
import com.banking.auth.dto.LoginRequest;
import com.banking.auth.dto.RefreshTokenRequest;
import com.banking.auth.dto.RegisterRequest;
import com.banking.auth.dto.UserResponse;
import com.banking.auth.exception.ConflictException;
import com.banking.auth.exception.ResourceNotFoundException;
import com.banking.auth.exception.UnauthorizedException;
import com.banking.auth.model.User;
import com.banking.auth.repository.UserRepository;
import com.banking.common.security.JwtService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private MeterRegistry meterRegistry;

    private User alice;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        alice = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .password("hashed-password")
                .firstName("Alice")
                .lastName("Smith")
                .phoneNumber("1234567890")
                .role(User.Role.USER)
                .enabled(true)
                .build();
    }

    private AuthService serviceWithRegistry() {
        return new AuthService(userRepository, passwordEncoder, jwtService,
                authenticationManager, userDetailsService, meterRegistry);
    }

    private UserDetails userDetails() {
        return org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("hashed-password")
                .authorities("ROLE_USER")
                .build();
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");
        request.setFirstName("Alice");
        request.setLastName("Smith");
        request.setPhoneNumber("1234567890");
        return request;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");
        return request;
    }

    // ----------------------------------------------------------------
    //  register
    // ----------------------------------------------------------------

    @Test
    void registerCreatesUserAndReturnsTokens() {
        AuthService service = serviceWithRegistry();

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());
        when(jwtService.generateToken(eq(userDetails()), eq(1L))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userDetails())).thenReturn("refresh-token");

        AuthResponse response = service.register(registerRequest());

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo("USER");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        AuthService service = serviceWithRegistry();
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username already exists");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        AuthService service = serviceWithRegistry();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already registered");
        verify(userRepository, never()).save(any(User.class));
    }

    // ----------------------------------------------------------------
    //  login
    // ----------------------------------------------------------------

    @Test
    void loginSucceedsAndRecordsSuccessMetric() {
        AuthService service = serviceWithRegistry();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(authenticationManager.authenticate(any()))
                .thenReturn(org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated("alice", null, java.util.List.of()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());
        when(jwtService.generateToken(eq(userDetails()), eq(1L))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userDetails())).thenReturn("refresh-token");

        AuthResponse response = service.login(loginRequest());

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(alice.getFailedLoginAttempts()).isZero();
        assertThat(meterRegistry.counter("banking.login.attempts", "result", "success").count()).isEqualTo(1);
        verify(userRepository).save(alice);
    }

    @Test
    void loginWithBadCredentialsIncrementsFailureAttempts() {
        AuthService service = serviceWithRegistry();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(alice.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(alice);
        assertThat(meterRegistry.counter("banking.login.attempts", "result", "failure").count()).isEqualTo(1);
    }

    @Test
    void loginLocksAccountAfterFifthFailedAttempt() {
        AuthService service = serviceWithRegistry();
        alice.setFailedLoginAttempts(4);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(alice.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(alice.getLockoutTime()).isNotNull();
    }

    @Test
    void loginRejectsLockedAccount() {
        AuthService service = serviceWithRegistry();
        alice.setLockoutTime(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(LockedException.class);
        verify(authenticationManager, never()).authenticate(any());
        verify(userRepository, never()).save(any(User.class));
        assertThat(meterRegistry.counter("banking.login.attempts", "result", "failure").count()).isEqualTo(1);
    }

    @Test
    void loginThrowsResourceNotFoundWhenUserMissing() {
        AuthService service = serviceWithRegistry();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Invalid credentials");
    }

    // ----------------------------------------------------------------
    //  refreshToken
    // ----------------------------------------------------------------

    @Test
    void refreshTokenIssuesNewAccessToken() {
        AuthService service = serviceWithRegistry();
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        when(jwtService.extractUsername("refresh-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());
        when(jwtService.isTokenValid("refresh-token", userDetails())).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(jwtService.generateToken(eq(userDetails()), eq(1L))).thenReturn("new-access-token");

        AuthResponse response = service.refreshToken(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void refreshTokenRejectsInvalidToken() {
        AuthService service = serviceWithRegistry();
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("bad-token");

        when(jwtService.extractUsername("bad-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());
        when(jwtService.isTokenValid("bad-token", userDetails())).thenReturn(false);

        assertThatThrownBy(() -> service.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshTokenThrowsWhenUserMissing() {
        AuthService service = serviceWithRegistry();
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        when(jwtService.extractUsername("refresh-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());
        when(jwtService.isTokenValid("refresh-token", userDetails())).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshToken(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // ----------------------------------------------------------------
    //  getUserByUsername
    // ----------------------------------------------------------------

    @Test
    void getUserByUsernameReturnsMappedUser() {
        AuthService service = serviceWithRegistry();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserResponse response = service.getUserByUsername("alice");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getFirstName()).isEqualTo("Alice");
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void getUserByUsernameThrowsWhenNotFound() {
        AuthService service = serviceWithRegistry();
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserByUsername("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // ----------------------------------------------------------------
    //  validateToken
    // ----------------------------------------------------------------

    @Test
    void validateTokenReturnsTrueForValidToken() {
        AuthService service = serviceWithRegistry();
        when(jwtService.extractUsername("token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails());
        when(jwtService.isTokenValid("token", userDetails())).thenReturn(true);

        assertThat(service.validateToken("token")).isTrue();
    }

    @Test
    void validateTokenReturnsFalseOnParsingFailure() {
        AuthService service = serviceWithRegistry();
        when(jwtService.extractUsername("junk")).thenThrow(new RuntimeException("bad token"));

        assertThat(service.validateToken("junk")).isFalse();
    }
}