package com.banking.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter ensuring every request carries a correlation ID.
 *
 * <p>If the client did not send an {@code X-Correlation-Id}, one is generated.
 * The ID is stored in the SLF4J MDC (key {@code correlationId}) so structured
 * log lines are correlated, and echoed back to the caller on the response
 * header so the client can log/follow it.
 *
 * <p>Conditional on a servlet web application so the reactive gateway
 * (WebFlux) does not attempt to register it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = CorrelationIdPropagationInterceptor.HEADER;
    public static final String MDC_KEY = CorrelationIdPropagationInterceptor.MDC_KEY;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}