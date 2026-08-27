package com.banking.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdPropagationInterceptorTest {

    @Mock
    private ClientHttpRequestExecution execution;

    @Mock
    private ClientHttpResponse response;

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdPropagationInterceptor.MDC_KEY);
    }

    @Test
    void propagatesCorrelationIdFromMdc() throws IOException {
        MDC.put(CorrelationIdPropagationInterceptor.MDC_KEY, "corr-42");
        when(execution.execute(any(), any(byte[].class))).thenReturn(response);

        ClientHttpRequestInterceptor interceptor = new CorrelationIdPropagationInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(CorrelationIdPropagationInterceptor.HEADER))
                .isEqualTo("corr-42");
        verify(execution).execute(request, new byte[0]);
    }

    @Test
    void doesNotSetHeaderWhenMdcEmpty() throws IOException {
        when(execution.execute(any(), any(byte[].class))).thenReturn(response);

        ClientHttpRequestInterceptor interceptor = new CorrelationIdPropagationInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(CorrelationIdPropagationInterceptor.HEADER)).isNull();
        verify(execution).execute(request, new byte[0]);
    }
}