package com.banking.gateway.filter;

import com.banking.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    private final JwtService jwtService;

    public AuthFilter(JwtService jwtService) {
        super(Config.class);
        this.jwtService = jwtService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);

            if (!jwtService.isTokenValid(token)) {
                return unauthorized(exchange, "Invalid or expired token");
            }

            try {
                String username = jwtService.extractUsername(token);
                Long userId = jwtService.extractUserId(token);

                // Forward user context to downstream services via headers
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(r -> r
                                .header("X-User-Name", username)
                                .header("X-User-Id", userId != null ? userId.toString() : "")
                        )
                        .build();

                log.debug("Authenticated request: user={} userId={} path={}",
                        username, userId, exchange.getRequest().getPath());

                return chain.filter(mutatedExchange);
            } catch (Exception e) {
                log.warn("Token processing error: {}", e.getMessage());
                return unauthorized(exchange, "Token processing failed");
            }
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.warn("Unauthorized request to {}: {}", exchange.getRequest().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // No config fields needed for now
    }
}
