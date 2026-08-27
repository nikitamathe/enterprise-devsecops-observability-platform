package com.banking.transaction.service;

import com.banking.transaction.dto.TransactionRequest;
import com.banking.transaction.dto.TransactionResponse;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.model.Transaction;
import com.banking.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;
    private final AccountCacheService accountCacheService;

    @Value("${services.account-service}")
    private String accountServiceUrl;

    @Value("${services.notification-service}")
    private String notificationServiceUrl;

    // ----------------------------------------------------------------
    //  Deposit
    // ----------------------------------------------------------------
    public TransactionResponse deposit(Long userId, TransactionRequest request) {
        validateAccountOwnership(request.getAccountNumber(), userId);
        Map<String, Object> account = fetchAccount(request.getAccountNumber());
        BigDecimal balanceBefore = new BigDecimal(account.get("balance").toString());
        return doDeposit(userId, request, balanceBefore);
    }

    @Transactional
    TransactionResponse doDeposit(Long userId, TransactionRequest request, BigDecimal balanceBefore) {
        Transaction txn = buildTransaction(userId, null, request.getAccountNumber(),
                Transaction.TransactionType.DEPOSIT, request.getAmount(),
                balanceBefore, request.getDescription());

        try {
            updateBalance(request.getAccountNumber(), request.getAmount(), "CREDIT");

            BigDecimal balanceAfter = balanceBefore.add(request.getAmount());
            txn.setBalanceAfter(balanceAfter);
            txn.setStatus(Transaction.TransactionStatus.SUCCESS);
            txn = transactionRepository.save(txn);

            sendNotification(userId, request.getAccountNumber(), txn.getTransactionReference(),
                    "DEPOSIT_SUCCESS", request.getAmount(), balanceAfter);

            log.info("Deposit successful: {} amount={}", txn.getTransactionReference(), request.getAmount());
            return mapToResponse(txn);
        } catch (Exception e) {
            txn.setStatus(Transaction.TransactionStatus.FAILED);
            txn.setFailureReason(e.getMessage());
            transactionRepository.save(txn);
            log.error("Deposit failed for account {}: {}", request.getAccountNumber(), e.getMessage());
            throw new TransactionException("Deposit failed: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    //  Withdrawal
    // ----------------------------------------------------------------
    public TransactionResponse withdraw(Long userId, TransactionRequest request) {
        validateAccountOwnership(request.getAccountNumber(), userId);
        Map<String, Object> account = fetchAccount(request.getAccountNumber());
        BigDecimal balanceBefore = new BigDecimal(account.get("balance").toString());

        if (balanceBefore.compareTo(request.getAmount()) < 0) {
            throw new TransactionException(
                    "Insufficient funds. Available: " + balanceBefore + ", Requested: " + request.getAmount());
        }
        return doWithdraw(userId, request, balanceBefore);
    }

    @Transactional
    TransactionResponse doWithdraw(Long userId, TransactionRequest request, BigDecimal balanceBefore) {
        Transaction txn = buildTransaction(userId, request.getAccountNumber(), null,
                Transaction.TransactionType.WITHDRAWAL, request.getAmount(),
                balanceBefore, request.getDescription());

        try {
            updateBalance(request.getAccountNumber(), request.getAmount(), "DEBIT");

            BigDecimal balanceAfter = balanceBefore.subtract(request.getAmount());
            txn.setBalanceAfter(balanceAfter);
            txn.setStatus(Transaction.TransactionStatus.SUCCESS);
            txn = transactionRepository.save(txn);

            sendNotification(userId, request.getAccountNumber(), txn.getTransactionReference(),
                    "WITHDRAWAL_SUCCESS", request.getAmount(), balanceAfter);

            log.info("Withdrawal successful: {} amount={}", txn.getTransactionReference(), request.getAmount());
            return mapToResponse(txn);
        } catch (Exception e) {
            txn.setStatus(Transaction.TransactionStatus.FAILED);
            txn.setFailureReason(e.getMessage());
            transactionRepository.save(txn);
            log.error("Withdrawal failed for account {}: {}", request.getAccountNumber(), e.getMessage());
            throw new TransactionException("Withdrawal failed: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    //  Transfer
    // ----------------------------------------------------------------
    public TransactionResponse transfer(Long userId, TransactionRequest request) {
        if (request.getFromAccountNumber() == null || request.getToAccountNumber() == null) {
            throw new TransactionException("Both fromAccountNumber and toAccountNumber are required for transfer");
        }
        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new TransactionException("Cannot transfer to the same account");
        }

        validateAccountOwnership(request.getFromAccountNumber(), userId);
        Map<String, Object> fromAccount = fetchAccount(request.getFromAccountNumber());
        BigDecimal balanceBefore = new BigDecimal(fromAccount.get("balance").toString());

        if (balanceBefore.compareTo(request.getAmount()) < 0) {
            throw new TransactionException(
                    "Insufficient funds. Available: " + balanceBefore + ", Requested: " + request.getAmount());
        }

        // Verify destination account exists
        fetchAccount(request.getToAccountNumber());
        return doTransfer(userId, request, balanceBefore);
    }

    @Transactional
    TransactionResponse doTransfer(Long userId, TransactionRequest request, BigDecimal balanceBefore) {
        Transaction txn = buildTransaction(userId, request.getFromAccountNumber(), request.getToAccountNumber(),
                Transaction.TransactionType.TRANSFER, request.getAmount(),
                balanceBefore, request.getDescription());

        try {
            updateBalance(request.getFromAccountNumber(), request.getAmount(), "DEBIT");
            updateBalance(request.getToAccountNumber(), request.getAmount(), "CREDIT");

            BigDecimal balanceAfter = balanceBefore.subtract(request.getAmount());
            txn.setBalanceAfter(balanceAfter);
            txn.setStatus(Transaction.TransactionStatus.SUCCESS);
            txn = transactionRepository.save(txn);

            sendNotification(userId, request.getFromAccountNumber(), txn.getTransactionReference(),
                    "TRANSFER_SUCCESS", request.getAmount(), balanceAfter);

            log.info("Transfer successful: {} amount={}", txn.getTransactionReference(), request.getAmount());
            return mapToResponse(txn);
        } catch (Exception e) {
            txn.setStatus(Transaction.TransactionStatus.FAILED);
            txn.setFailureReason(e.getMessage());
            transactionRepository.save(txn);
            log.error("Transfer failed from {} to {}: {}", request.getFromAccountNumber(),
                    request.getToAccountNumber(), e.getMessage());
            throw new TransactionException("Transfer failed: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    //  Query methods
    // ----------------------------------------------------------------
    public Page<TransactionResponse> getTransactionsByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    public Page<TransactionResponse> getTransactionsByAccount(String accountNumber, int page, int size) {
        return transactionRepository.findByAccountNumber(accountNumber, PageRequest.of(page, size))
                .map(this::mapToResponse);
    }

    public TransactionResponse getTransactionByReference(String reference) {
        Transaction txn = transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> new TransactionException("Transaction not found: " + reference));
        return mapToResponse(txn);
    }

    public List<TransactionResponse> getTransactionsByUserIdAndDateRange(
            Long userId, LocalDateTime from, LocalDateTime to) {
        return transactionRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, from, to)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    //  Helpers
    // ----------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchAccount(String accountNumber) {
        return accountCacheService.fetchAccount(accountNumber, accountServiceUrl);
    }

    @SuppressWarnings("unchecked")
    private void validateAccountOwnership(String accountNumber, Long userId) {
        Map<String, Object> account = fetchAccount(accountNumber);
        Object accountUserId = account.get("userId");
        if (accountUserId == null || !userId.equals(Long.valueOf(accountUserId.toString()))) {
            throw new TransactionException("Account " + accountNumber + " does not belong to the current user");
        }
        String status = (String) account.get("status");
        if (!"ACTIVE".equals(status)) {
            throw new TransactionException("Account " + accountNumber + " is not active");
        }
    }

    private void updateBalance(String accountNumber, BigDecimal amount, String operation) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "amount", amount,
                "operationType", operation
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.patchForObject(
                accountServiceUrl + "/api/accounts/" + accountNumber + "/balance",
                entity,
                Void.class
        );
    }

    private void sendNotification(Long userId, String accountNumber, String reference,
                                   String type, BigDecimal amount, BigDecimal balance) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                    "type", type,
                    "userId", userId,
                    "accountNumber", accountNumber,
                    "transactionReference", reference,
                    "amount", amount,
                    "balance", balance
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(notificationServiceUrl + "/api/notifications/internal", entity, Void.class);
        } catch (Exception e) {
            log.warn("Failed to send notification for ref {}: {}", reference, e.getMessage());
        }
    }

    private Transaction buildTransaction(Long userId, String from, String to,
                                          Transaction.TransactionType type,
                                          BigDecimal amount, BigDecimal balanceBefore,
                                          String description) {
        return Transaction.builder()
                .transactionReference(generateReference())
                .userId(userId)
                .fromAccountNumber(from)
                .toAccountNumber(to)
                .transactionType(type)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .status(Transaction.TransactionStatus.PENDING)
                .description(description)
                .build();
    }

    private String generateReference() {
        return "TXN" + UUID.randomUUID();
    }

    private TransactionResponse mapToResponse(Transaction txn) {
        return mapToResponse(txn, txn.getBalanceBefore(), txn.getBalanceAfter(), txn.getAmount());
    }

    private TransactionResponse mapToResponse(Transaction txn, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                               BigDecimal amount) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .transactionReference(txn.getTransactionReference())
                .userId(txn.getUserId())
                .fromAccountNumber(txn.getFromAccountNumber())
                .toAccountNumber(txn.getToAccountNumber())
                .transactionType(txn.getTransactionType().name())
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status(txn.getStatus().name())
                .description(txn.getDescription())
                .failureReason(txn.getFailureReason())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
