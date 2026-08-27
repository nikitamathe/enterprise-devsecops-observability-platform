package com.banking.account.security;

import com.banking.common.security.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    void setsAuthenticationWithRoleUserForValidToken() throws Exception {
        MockHttpServletRequest request = requestWithBearer("valid-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        when(jwtService.extractUsername("valid-token")).thenReturn("alice");
        when(jwtService.isTokenExpired("valid-token")).thenReturn(false);

        filter.doFilter(request, resp, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("alice");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    void skipsWithoutAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = requestWithBearer(null);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(request, resp, (FilterChain) (req, res) -> {});

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsExpiredTokenWithUnauthorized() throws Exception {
        MockHttpServletRequest request = requestWithBearer("expired-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        when(jwtService.extractUsername("expired-token")).thenReturn("alice");
        when(jwtService.isTokenExpired("expired-token")).thenReturn(true);

        filter.doFilter(request, resp, (FilterChain) (req, res) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("Invalid or expired token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsTokenWithNullUsername() throws Exception {
        MockHttpServletRequest request = requestWithBearer("token-no-user");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        when(jwtService.extractUsername("token-no-user")).thenReturn(null);

        filter.doFilter(request, resp, (FilterChain) (req, res) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsMalformedTokenWithUnauthorized() throws Exception {
        MockHttpServletRequest request = requestWithBearer("junk");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        when(jwtService.extractUsername("junk")).thenThrow(new RuntimeException("bad token"));

        filter.doFilter(request, resp, (FilterChain) (req, res) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("Invalid token");
    }
}