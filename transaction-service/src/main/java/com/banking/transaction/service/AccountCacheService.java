package com.banking.transaction.service;

import com.banking.common.dto.ApiResponse;
import com.banking.common.logging.PiiSanitizer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountCacheService {

    private final RestTemplate restTemplate;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Cacheable(value = "accounts", key = "#accountNumber")
    public Map<String, Object> fetchAccount(String accountNumber, String accountServiceUrl) {
        log.debug("Cache miss — fetching account {} from account-service", PiiSanitizer.maskAccountNumber(accountNumber));
        try {
            CircuitBreaker breaker = circuitBreakerFactory.create("accountService");
            return breaker.run(() -> doFetchAccount(accountNumber, accountServiceUrl), this::onAccountServiceFailure);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not reach account service: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doFetchAccount(String accountNumber, String accountServiceUrl) {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                accountServiceUrl + "/api/accounts/number/" + accountNumber,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        if (response.getBody() != null && response.getBody().isSuccess()) {
            return (Map<String, Object>) response.getBody().getData();
        }
        throw new RuntimeException("Account not found: " + accountNumber);
    }

    private Map<String, Object> onAccountServiceFailure(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.warn("Circuit breaker 'accountService' is OPEN — skipping account-service lookup: {}",
                    PiiSanitizer.maskAccountNumbers(throwable.getMessage()));
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException("Account service call failed: " + throwable.getMessage(), throwable);
    }
}
