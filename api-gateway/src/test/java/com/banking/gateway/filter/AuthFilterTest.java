package com.banking.gateway.filter;

import com.banking.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFilterTest {

    private StubJwtService jwtService;
    private GatewayFilter gatewayFilter;

    @BeforeEach
    void setUp() {
        jwtService = new StubJwtService();
        gatewayFilter = new AuthFilter(jwtService).apply(new AuthFilter.Config());
    }

    private MockServerWebExchange exchangeWithAuthorization(String authHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/accounts");
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private GatewayFilterChain chain(AtomicBoolean called, AtomicReference<ServerWebExchange> received) {
        return exchange -> {
            called.set(true);
            received.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void forwardsUserContextHeadersForValidToken() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer valid-token");
        AtomicBoolean called = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> received = new AtomicReference<>();

        jwtService.username = "alice";
        jwtService.userId = 42L;

        gatewayFilter.filter(exchange, chain(called, received)).block();

        assertThat(called).isTrue();
        assertThat(received.get().getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("alice");
        assertThat(received.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsMissingAuthorizationHeader() {
        MockServerWebExchange exchange = exchangeWithAuthorization(null);
        AtomicBoolean called = new AtomicBoolean(false);

        gatewayFilter.filter(exchange, chain(called, new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(called).isFalse();
    }

    @Test
    void rejectsNonBearerHeader() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Basic abc");
        AtomicBoolean called = new AtomicBoolean(false);

        gatewayFilter.filter(exchange, chain(called, new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(called).isFalse();
    }

    @Test
    void rejectsInvalidToken() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer invalid-token");
        AtomicBoolean called = new AtomicBoolean(false);

        jwtService.valid = false;

        gatewayFilter.filter(exchange, chain(called, new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(called).isFalse();
    }

    @Test
    void rejectsWhenTokenProcessingFails() {
        MockServerWebExchange exchange = exchangeWithAuthorization("Bearer boom");
        AtomicBoolean called = new AtomicBoolean(false);

        gatewayFilter.filter(exchange, chain(called, new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(called).isFalse();
    }

    private static class StubJwtService extends JwtService {
        boolean valid = true;
        String username = "alice";
        Long userId = 42L;

        @Override
        public boolean isTokenValid(String token) {
            return valid;
        }

        @Override
        public String extractUsername(String token) {
            if ("boom".equals(token)) {
                throw new RuntimeException("bad token");
            }
            return username;
        }

        @Override
        public Long extractUserId(String token) {
            return userId;
        }
    }
}