package com.banking.transaction.service;

import com.banking.transaction.dto.TransactionRequest;
import com.banking.transaction.dto.TransactionResponse;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.model.Transaction;
import com.banking.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AccountCacheService accountCacheService;

    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private TransactionService transactionService;

    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, restTemplate,
                accountCacheService, circuitBreakerFactory, meterRegistry);
        ReflectionTestUtils.setField(transactionService, "accountServiceUrl",
                "http://account-service:8082");
        ReflectionTestUtils.setField(transactionService, "notificationServiceUrl",
                "http://notification-service:8084");

        breaker = org.mockito.Mockito.mock(CircuitBreaker.class);
        when(circuitBreakerFactory.create(anyString())).thenReturn(breaker);
    }

    private void circuitBreakerRunsSupplier() {
        when(breaker.run(any(Supplier.class), any(Function.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
    }

    private void circuitBreakerThrows(RuntimeException ex) {
        when(breaker.run(any(Supplier.class), any(Function.class))).thenThrow(ex);
    }

    private Map<String, Object> account(String number, Long userId, String status, String balance) {
        return Map.of("accountNumber", number, "userId", userId, "status", status,
                "balance", new BigDecimal(balance));
    }

    private TransactionRequest request(Transaction.TransactionType type, String amount,
                                       String from, String to) {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType(type);
        request.setAmount(new BigDecimal(amount));
        request.setFromAccountNumber(from);
        request.setToAccountNumber(to);
        return request;
    }

    private Transaction txn(Long id) {
        return Transaction.builder()
                .id(id)
                .transactionReference("TXN-reference")
                .userId(7L)
                .amount(new BigDecimal("50"))
                .balanceBefore(new BigDecimal("100"))
                .balanceAfter(new BigDecimal("150"))
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .status(Transaction.TransactionStatus.SUCCESS)
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    // ----------------------------------------------------------------
    //  deposit
    // ----------------------------------------------------------------

    @Test
    void depositSucceedsAndRecordsMetric() {
        circuitBreakerRunsSupplier();
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "100.00"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest req = request(Transaction.TransactionType.DEPOSIT, "50",
                null, null);
        req.setAccountNumber("ACC100000000001");

        TransactionResponse response = transactionService.deposit(7L, req);

        assertThat(response.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(response.getBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        verify(restTemplate).patchForObject(eq("http://account-service:8082/api/accounts/ACC100000000001/balance"),
                any(), eq(Void.class));
        assertThat(meterRegistry.counter("banking.transactions", "type", "DEPOSIT", "status", "SUCCESS").count())
                .isEqualTo(1);
    }

    @Test
    void depositRecordsFailureAndThrowsWhenAccountServiceDown() {
        circuitBreakerThrows(new RuntimeException("account service unavailable"));
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "100.00"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        TransactionRequest req = request(Transaction.TransactionType.DEPOSIT, "50",
                null, null);
        req.setAccountNumber("ACC100000000001");

        assertThatThrownBy(() -> transactionService.deposit(7L, req))
                .isInstanceOf(TransactionException.class);

        assertThat(meterRegistry.counter("banking.transactions", "type", "DEPOSIT", "status", "FAILED").count())
                .isEqualTo(1);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void depositSucceedsEvenWhenNotificationFails() {
        circuitBreakerRunsSupplier();
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "100.00"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(restTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(new RuntimeException("notification service down"));

        TransactionRequest req = request(Transaction.TransactionType.DEPOSIT, "50",
                null, null);
        req.setAccountNumber("ACC100000000001");

        TransactionResponse response = transactionService.deposit(7L, req);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
    }

    // ----------------------------------------------------------------
    //  withdraw
    // ----------------------------------------------------------------

    @Test
    void withdrawSucceedsAndRecordsMetric() {
        circuitBreakerRunsSupplier();
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "100.00"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest req = request(Transaction.TransactionType.WITHDRAWAL, "40",
                null, null);
        req.setAccountNumber("ACC100000000001");

        TransactionResponse response = transactionService.withdraw(7L, req);

        assertThat(response.getBalanceAfter()).isEqualByComparingTo("60.00");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(meterRegistry.counter("banking.transactions", "type", "WITHDRAWAL", "status", "SUCCESS").count())
                .isEqualTo(1);
    }

    @Test
    void withdrawRejectsInsufficientFundsBeforeCallingAccountService() {
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "40.00"));

        TransactionRequest req = request(Transaction.TransactionType.WITHDRAWAL, "50",
                null, null);
        req.setAccountNumber("ACC100000000001");

        assertThatThrownBy(() -> transactionService.withdraw(7L, req))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("Insufficient funds");
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void withdrawRecordsFailureWhenAccountServiceFails() {
        circuitBreakerThrows(new RuntimeException("account service unavailable"));
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "100.00"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest req = request(Transaction.TransactionType.WITHDRAWAL, "40",
                null, null);
        req.setAccountNumber("ACC100000000001");

        assertThatThrownBy(() -> transactionService.withdraw(7L, req))
                .isInstanceOf(TransactionException.class);
        assertThat(meterRegistry.counter("banking.transactions", "type", "WITHDRAWAL", "status", "FAILED").count())
                .isEqualTo(1);
    }

    // ----------------------------------------------------------------
    //  transfer
    // ----------------------------------------------------------------

    @Test
    void transferSucceedsAndDebitsCreditsBothAccounts() {
        circuitBreakerRunsSupplier();
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "100.00"));
        when(accountCacheService.fetchAccount("ACC100000000002", "http://account-service:8082"))
                .thenReturn(account("ACC100000000002", 8L, "ACTIVE", "0.00"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest req = request(Transaction.TransactionType.TRANSFER, "50",
                "ACC100000000001", "ACC100000000002");

        TransactionResponse response = transactionService.transfer(7L, req);

        assertThat(response.getBalanceAfter()).isEqualByComparingTo("50.00");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        verify(restTemplate, times(2)).patchForObject(anyString(), any(), eq(Void.class));
        assertThat(meterRegistry.counter("banking.transactions", "type", "TRANSFER", "status", "SUCCESS").count())
                .isEqualTo(1);
    }

    @Test
    void transferRejectsMissingAccountNumbers() {
        TransactionRequest req = request(Transaction.TransactionType.TRANSFER, "50",
                null, null);

        assertThatThrownBy(() -> transactionService.transfer(7L, req))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("Both fromAccountNumber and toAccountNumber");
    }

    @Test
    void transferRejectsSameAccount() {
        TransactionRequest req = request(Transaction.TransactionType.TRANSFER, "50",
                "ACC100000000001", "ACC100000000001");

        assertThatThrownBy(() -> transactionService.transfer(7L, req))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void transferRejectsAccountOwnedByAnotherUser() {
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 999L, "ACTIVE", "100.00"));

        TransactionRequest req = request(Transaction.TransactionType.TRANSFER, "50",
                "ACC100000000001", "ACC100000000002");

        assertThatThrownBy(() -> transactionService.transfer(7L, req))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("does not belong to the current user");
    }

    @Test
    void transferRejectsInactiveAccount() {
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "INACTIVE", "100.00"));

        TransactionRequest req = request(Transaction.TransactionType.TRANSFER, "50",
                "ACC100000000001", "ACC100000000002");

        assertThatThrownBy(() -> transactionService.transfer(7L, req))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    void transferRejectsInsufficientFunds() {
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "40.00"));

        TransactionRequest req = request(Transaction.TransactionType.TRANSFER, "50",
                "ACC100000000001", "ACC100000000002");

        assertThatThrownBy(() -> transactionService.transfer(7L, req))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void transferPropagatesWhenDestinationNotFound() {
        when(accountCacheService.fetchAccount("ACC100000000001", "http://account-service:8082"))
                .thenReturn(account("ACC100000000001", 7L, "ACTIVE", "100.00"));
        when(accountCacheService.fetchAccount("ACC100000000002", "http://account-service:8082"))
                .thenThrow(new RuntimeException("Account not found: ACC100000000002"));

        TransactionRequest req = request(Transaction.TransactionType.TRANSFER, "50",
                "ACC100000000001", "ACC100000000002");

        assertThatThrownBy(() -> transactionService.transfer(7L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Account not found");
    }

    // ----------------------------------------------------------------
    //  Query methods
    // ----------------------------------------------------------------

    @Test
    void getTransactionsByUserIdMapsPage() {
        Page<Transaction> page = new PageImpl<>(List.of(txn(1L)));
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(page);

        Page<TransactionResponse> result = transactionService.getTransactionsByUserId(7L, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionReference()).isEqualTo("TXN-reference");
    }

    @Test
    void getTransactionsByAccountMapsPage() {
        Page<Transaction> page = new PageImpl<>(List.of(txn(1L)));
        when(transactionRepository.findByAccountNumber(eq("ACC100000000001"), any(Pageable.class)))
                .thenReturn(page);

        Page<TransactionResponse> result = transactionService.getTransactionsByAccount("ACC100000000001", 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getTransactionByReferenceReturnsTransaction() {
        when(transactionRepository.findByTransactionReference("TXN-reference"))
                .thenReturn(Optional.of(txn(1L)));

        TransactionResponse response = transactionService.getTransactionByReference("TXN-reference");

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void getTransactionByReferenceThrowsWhenNotFound() {
        when(transactionRepository.findByTransactionReference("TXN-missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionByReference("TXN-missing"))
                .isInstanceOf(TransactionException.class);
    }

    @Test
    void getTransactionsByUserIdAndDateRangeMapsPage() {
        Page<Transaction> page = new PageImpl<>(List.of(txn(1L)));
        when(transactionRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(7L), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

        Page<TransactionResponse> result = transactionService.getTransactionsByUserIdAndDateRange(
                7L, LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2025, 1, 31, 0, 0),
                0, 20);

        assertThat(result.getContent()).hasSize(1);
    }
}