package com.banking.transaction.service;

import com.banking.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountCacheServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    private final CircuitBreaker breaker = org.mockito.Mockito.mock(CircuitBreaker.class);

    private AccountCacheService accountCacheService;

    private static final String URL = "http://account-service:8082";

    @BeforeEach
    void setUp() {
        accountCacheService = new AccountCacheService(restTemplate, circuitBreakerFactory);
        when(circuitBreakerFactory.create(anyString())).thenReturn(breaker);
    }

    private void circuitBreakerRunsSupplierWithFallback() {
        when(breaker.run(any(Supplier.class), any(Function.class))).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(0);
            Function<Throwable, ?> fallback = inv.getArgument(1);
            try {
                return supplier.get();
            } catch (RuntimeException ex) {
                return fallback.apply(ex);
            }
        });
    }

    private void mockExchange(ApiResponse<Map<String, Object>> body) {
        when(restTemplate.exchange(eq(URL + "/api/accounts/number/ACC100000000001"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    @Test
    void fetchAccountReturnsDataOnSuccess() {
        circuitBreakerRunsSupplierWithFallback();
        mockExchange(ApiResponse.success("ok", Map.of(
                "accountNumber", "ACC100000000001",
                "balance", new BigDecimal("100.00"))));

        Map<String, Object> result = accountCacheService.fetchAccount("ACC100000000001", URL);

        assertThat(result.get("accountNumber")).isEqualTo("ACC100000000001");
        assertThat(result.get("balance")).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void fetchAccountThrowsWhenResponseIsError() {
        circuitBreakerRunsSupplierWithFallback();
        mockExchange(ApiResponse.error("not found"));

        assertThatThrownBy(() -> accountCacheService.fetchAccount("ACC100000000001", URL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Account not found: ACC100000000001");
    }

    @Test
    void fetchAccountThrowsWhenRestCallFails() {
        circuitBreakerRunsSupplierWithFallback();
        when(restTemplate.exchange(eq(URL + "/api/accounts/number/ACC100000000001"),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> accountCacheService.fetchAccount("ACC100000000001", URL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection refused");
    }
}