package com.banking.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        String secret = Base64.getEncoder().encodeToString(key);

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", secret);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L);
    }

    private UserDetails userDetails() {
        return org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("password")
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    void extractUsernameReturnsSubject() {
        String token = jwtService.generateToken(userDetails());
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void generateTokenWithUserIdAllowsExtractingUserIdAndSubject() {
        String token = jwtService.generateToken(userDetails(), 42L);
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void generateRefreshTokenProducesValidToken() {
        String refresh = jwtService.generateRefreshToken(userDetails());
        assertThat(jwtService.extractUsername(refresh)).isEqualTo("alice");
        assertThat(jwtService.isTokenValid(refresh)).isTrue();
    }

    @Test
    void isTokenValidReturnsTrueForMatchingUser() {
        String token = jwtService.generateToken(userDetails());
        assertThat(jwtService.isTokenValid(token, userDetails())).isTrue();
    }

    @Test
    void isTokenValidReturnsFalseForDifferentUser() {
        String token = jwtService.generateToken(userDetails());
        UserDetails bob = org.springframework.security.core.userdetails.User.withUsername("bob")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        assertThat(jwtService.isTokenValid(token, bob)).isFalse();
    }

    @Test
    void expiredTokenThrowsOnExpiryCheckAndIsRejectedByIsTokenValid() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1_000L);
        String token = jwtService.generateToken(userDetails());
        assertThatThrownBy(() -> jwtService.isTokenExpired(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(userDetails());
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(RuntimeException.class);
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void extractClaimReturnsMappedValue() {
        String token = jwtService.generateToken(userDetails());
        String username = jwtService.extractClaim(token, claims -> claims.getSubject());
        assertThat(username).isEqualTo("alice");
    }
}