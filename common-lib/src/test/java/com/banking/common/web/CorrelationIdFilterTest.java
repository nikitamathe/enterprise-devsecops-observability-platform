package com.banking.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/test");
    }

    private FilterChain capturingChain(AtomicReference<String> mdcCapture) {
        return (req, res) -> mdcCapture.set(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void generatesCorrelationIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcCapture = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(mdcCapture));

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(mdcCapture.get()).isEqualTo(correlationId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void echoesClientProvidedCorrelationId() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(CorrelationIdFilter.HEADER, "client-correlation-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcCapture = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(mdcCapture));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("client-correlation-123");
        assertThat(mdcCapture.get()).isEqualTo("client-correlation-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderBlank() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(CorrelationIdFilter.HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void removesCorrelationIdFromMdcAfterChain() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank();
        });

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}