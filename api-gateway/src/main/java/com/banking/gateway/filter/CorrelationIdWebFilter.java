package com.banking.gateway.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive edge filter that guarantees every request entering the platform has
 * a correlation ID.
 *
 * <p>If the client did not send {@code X-Correlation-Id}, one is generated.
 * The ID is set on the response header and injected into the outgoing request
 * headers so Spring Cloud Gateway forwards it to the routed microservice.
 * Gateway-side log lines get the ID via the SLF4J MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        exchange.getResponse().getHeaders().add(HEADER, correlationId);

        final String headerCorrelationId = correlationId;
        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.header(HEADER, headerCorrelationId))
                .build();

        return chain.filter(mutated).doFinally(signalType -> MDC.remove(MDC_KEY));
    }
}