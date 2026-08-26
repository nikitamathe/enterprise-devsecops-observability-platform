package com.banking.account.service;

import com.banking.account.dto.AccountResponse;
import com.banking.account.dto.BalanceUpdateRequest;
import com.banking.account.dto.CreateAccountRequest;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.InactiveAccountException;
import com.banking.account.exception.InsufficientFundsException;
import com.banking.account.model.Account;
import com.banking.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final RestTemplate restTemplate;

    @Value("${notification-service.url}")
    private String notificationServiceUrl;

    @Transactional
    public AccountResponse createAccount(Long userId, CreateAccountRequest request) {
        String accountNumber = generateAccountNumber();
        while (accountRepository.existsByAccountNumber(accountNumber)) {
            accountNumber = generateAccountNumber();
        }

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .userId(userId)
                .accountHolderName(request.getAccountHolderName())
                .accountType(request.getAccountType())
                .balance(BigDecimal.ZERO)
                .status(Account.AccountStatus.ACTIVE)
                .build();

        account = accountRepository.save(account);
        log.info("Created account {} for user {}", accountNumber, userId);

        return mapToResponse(account);
    }

    public List<AccountResponse> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public AccountResponse getAccountById(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + accountId));
        return mapToResponse(account);
    }

    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
        return mapToResponse(account);
    }

    @Transactional
    public AccountResponse updateBalance(String accountNumber, BalanceUpdateRequest request) {
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new InactiveAccountException("Account is not active: " + accountNumber);
        }

        if (request.getOperationType() == BalanceUpdateRequest.OperationType.DEBIT) {
            if (account.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds. Available: " + account.getBalance() + ", Requested: " + request.getAmount()
                );
            }
            account.setBalance(account.getBalance().subtract(request.getAmount()));

            // Send low balance alert if balance drops below 500
            if (account.getBalance().compareTo(new BigDecimal("500")) < 0) {
                sendLowBalanceNotification(account);
            }
        } else {
            account.setBalance(account.getBalance().add(request.getAmount()));
        }

        account = accountRepository.save(account);
        log.info("Updated balance for account {}: {} {}", accountNumber, request.getOperationType(), request.getAmount());

        return mapToResponse(account);
    }

    @Transactional
    public AccountResponse closeAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        account.setStatus(Account.AccountStatus.CLOSED);
        account = accountRepository.save(account);
        log.info("Closed account {}", account.getAccountNumber());

        return mapToResponse(account);
    }

    private void sendLowBalanceNotification(Account account) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                    "type", "LOW_BALANCE",
                    "userId", account.getUserId(),
                    "accountNumber", account.getAccountNumber(),
                    "balance", account.getBalance()
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(notificationServiceUrl + "/api/notifications/internal", entity, Void.class);
        } catch (Exception e) {
            log.warn("Failed to send low balance notification for account {}: {}", account.getAccountNumber(), e.getMessage());
        }
    }

    private String generateAccountNumber() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("ACC");
        for (int i = 0; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .userId(account.getUserId())
                .accountHolderName(account.getAccountHolderName())
                .accountType(account.getAccountType().name())
                .balance(account.getBalance())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
