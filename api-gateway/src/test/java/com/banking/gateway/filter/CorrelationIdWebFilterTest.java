package com.banking.gateway.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void generatesCorrelationIdWhenHeaderMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/accounts").build());
        AtomicReference<String> received = new AtomicReference<>();
        WebFilterChain chain = e -> {
            received.set(e.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String header = exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        assertThat(header).isNotBlank();
        assertThat(received.get()).isEqualTo(header);
        assertThat(MDC.get(CorrelationIdWebFilter.MDC_KEY)).isNull();
    }

    @Test
    void preservesClientProvidedCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/accounts")
                        .header(CorrelationIdWebFilter.HEADER, "corr-123")
                        .build());
        AtomicReference<String> received = new AtomicReference<>();
        WebFilterChain chain = e -> {
            received.set(e.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER))
                .isEqualTo("corr-123");
        assertThat(received.get()).isEqualTo("corr-123");
    }

    @Test
    void treatsBlankCorrelationIdAsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/accounts")
                        .header(CorrelationIdWebFilter.HEADER, "   ")
                        .build());

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER))
                .isNotBlank();
    }

    @Test
    void removesMdcEntryAfterChainCompletes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/accounts").build());
        AtomicReference<String> duringChain = new AtomicReference<>();
        WebFilterChain chain = e -> {
            duringChain.set(MDC.get(CorrelationIdWebFilter.MDC_KEY));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(duringChain.get()).isNotBlank();
        assertThat(MDC.get(CorrelationIdWebFilter.MDC_KEY)).isNull();
    }
}