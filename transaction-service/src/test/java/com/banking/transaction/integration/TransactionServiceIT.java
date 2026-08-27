package com.banking.transaction.integration;

import com.banking.transaction.dto.TransactionRequest;
import com.banking.transaction.dto.TransactionResponse;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.model.Transaction;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.service.AccountCacheService;
import com.banking.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=VGhpcyBJcyBBIExvbmcgU2VjcmV0IEtleSBGb3IgSmFzb24gMzIgU2lnbmF0dXJl"
})
@Transactional
class TransactionServiceIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("banking_db");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockitoBean
    private AccountCacheService accountCacheService;

    @MockitoBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Account ownership/balance lookups are stubbed per-test; outbound balance
        // sync to account-service and notification calls are exercised as no-ops
        // on the mocked RestTemplate.
    }

    private void stubAccount(String accountNumber, Long userId, String status, String balance) {
        when(accountCacheService.fetchAccount(eq(accountNumber), anyString()))
                .thenReturn(Map.of(
                        "balance", new BigDecimal(balance),
                        "userId", userId,
                        "status", status
                ));
    }

    private TransactionRequest depositRequest(String accountNumber, String amount) {
        return TransactionRequest.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal(amount))
                .accountNumber(accountNumber)
                .build();
    }

    private TransactionRequest withdrawRequest(String accountNumber, String amount) {
        return TransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal(amount))
                .accountNumber(accountNumber)
                .build();
    }

    private TransactionRequest transferRequest(String from, String to, String amount) {
        return TransactionRequest.builder()
                .transactionType(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal(amount))
                .fromAccountNumber(from)
                .toAccountNumber(to)
                .build();
    }

    @Test
    void depositPersistsSuccessfulTransaction() {
        stubAccount("ACC100000000001", 7L, "ACTIVE", "100.00");

        TransactionResponse response = transactionService.deposit(7L, depositRequest("ACC100000000001", "50.00"));

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(response.getBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(response.getTransactionReference()).isNotBlank();

        Transaction persisted = transactionRepository.findByTransactionReference(response.getTransactionReference())
                .orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(Transaction.TransactionStatus.SUCCESS);
        assertThat(persisted.getTransactionType()).isEqualTo(Transaction.TransactionType.DEPOSIT);
        assertThat(persisted.getToAccountNumber()).isEqualTo("ACC100000000001");
        assertThat(persisted.getBalanceAfter()).isEqualByComparingTo("150.00");
    }

    @Test
    void withdrawPersistsSuccessfulTransaction() {
        stubAccount("ACC100000000001", 7L, "ACTIVE", "100.00");

        TransactionResponse response = transactionService.withdraw(7L, withdrawRequest("ACC100000000001", "40.00"));

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getBalanceAfter()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawWithInsufficientFundsThrowsAndPersistsNothing() {
        stubAccount("ACC100000000001", 7L, "ACTIVE", "10.00");

        assertThatThrownBy(() -> transactionService.withdraw(7L, withdrawRequest("ACC100000000001", "50.00")))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void withdrawFromInactiveAccountThrows() {
        stubAccount("ACC100000000001", 7L, "CLOSED", "100.00");

        assertThatThrownBy(() -> transactionService.withdraw(7L, withdrawRequest("ACC100000000001", "50.00")))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void transferPersistsSuccessfulTransaction() {
        stubAccount("ACC100000000001", 7L, "ACTIVE", "100.00");
        stubAccount("ACC100000000002", 7L, "ACTIVE", "50.00");

        TransactionResponse response = transactionService.transfer(7L, transferRequest(
                "ACC100000000001", "ACC100000000002", "30.00"));

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getFromAccountNumber()).isEqualTo("ACC100000000001");
        assertThat(response.getToAccountNumber()).isEqualTo("ACC100000000002");
        assertThat(response.getBalanceAfter()).isEqualByComparingTo("70.00");
    }

    @Test
    void failedDepositPersistsFailedTransaction() {
        stubAccount("ACC100000000001", 7L, "ACTIVE", "100.00");
        doThrow(new RuntimeException("account service unavailable"))
                .when(restTemplate).patchForObject(anyString(), any(HttpEntity.class), eq(Void.class));

        assertThatThrownBy(() -> transactionService.deposit(7L, depositRequest("ACC100000000001", "50.00")))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("Deposit failed");

        Transaction persisted = transactionRepository.findAll().stream()
                .filter(t -> t.getTransactionType() == Transaction.TransactionType.DEPOSIT)
                .findFirst()
                .orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(Transaction.TransactionStatus.FAILED);
        assertThat(persisted.getFailureReason()).contains("account service unavailable");
    }

    @Test
    void transactionHistoryIsPersistedAndQueryable() {
        stubAccount("ACC100000000001", 7L, "ACTIVE", "100.00");
        transactionService.deposit(7L, depositRequest("ACC100000000001", "50.00"));

        stubAccount("ACC100000000001", 7L, "ACTIVE", "150.00");
        transactionService.deposit(7L, depositRequest("ACC100000000001", "25.00"));

        Page<TransactionResponse> history = transactionService.getTransactionsByUserIdAndDateRange(
                7L,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1),
                0,
                20);

        assertThat(history.getContent()).hasSize(2);
        assertThat(history.getContent().get(0).getBalanceAfter())
                .isEqualByComparingTo("175.00");
    }
}