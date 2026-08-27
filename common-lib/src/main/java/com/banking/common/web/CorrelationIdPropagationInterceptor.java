package com.banking.common.web;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate interceptor that forwards the current request's correlation ID
 * (from the SLF4J MDC) on every outbound HTTP call. This keeps a single
 * X-Correlation-Id traceable across service-to-service calls originating from
 * one user request.
 */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            request.getHeaders().set(HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}