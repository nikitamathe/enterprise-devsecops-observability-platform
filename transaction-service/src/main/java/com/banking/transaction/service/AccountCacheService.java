package com.banking.transaction.service;

import com.banking.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(value = "accounts", key = "#accountNumber")
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchAccount(String accountNumber, String accountServiceUrl) {
        log.debug("Cache miss — fetching account {} from account-service", accountNumber);
        try {
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
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not reach account service: " + e.getMessage());
        }
    }
}
