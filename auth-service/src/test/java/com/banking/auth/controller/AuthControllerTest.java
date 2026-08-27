package com.banking.auth.controller;

import com.banking.auth.dto.AuthResponse;
import com.banking.auth.dto.UserResponse;
import com.banking.auth.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new com.banking.auth.exception.GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AuthResponse authResponse() {
        return AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .userId(1L)
                .username("alice")
                .email("alice@example.com")
                .role("USER")
                .build();
    }

    private UserResponse userResponse() {
        return UserResponse.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .phoneNumber("1234567890")
                .role("USER")
                .enabled(true)
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    @Test
    void registerReturnsCreatedWithAuthResponse() throws Exception {
        when(authService.register(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\","
                                + "\"password\":\"P@ssw0rd12345\",\"firstName\":\"Alice\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginReturnsAuthResponse() throws Exception {
        when(authService.login(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void refreshReturnsAuthResponse() throws Exception {
        when(authService.refreshToken(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        when(authService.getUserByUsername("alice")).thenReturn(userResponse());

        UserDetails details = org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("pw")
                .authorities("ROLE_USER")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void validateReturnsTokenValidity() throws Exception {
        when(authService.validateToken("good-token")).thenReturn(true);

        mockMvc.perform(get("/api/auth/validate").param("token", "good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void getUserByUsernameReturnsUser() throws Exception {
        when(authService.getUserByUsername(anyString())).thenReturn(userResponse());

        mockMvc.perform(get("/api/auth/user/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }
}