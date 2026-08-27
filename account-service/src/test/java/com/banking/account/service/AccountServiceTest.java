package com.banking.account.service;

import com.banking.account.dto.AccountResponse;
import com.banking.account.dto.BalanceUpdateRequest;
import com.banking.account.dto.CreateAccountRequest;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.InactiveAccountException;
import com.banking.account.exception.InsufficientFundsException;
import com.banking.account.model.Account;
import com.banking.account.repository.AccountRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RestTemplate restTemplate;

    private MeterRegistry meterRegistry;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        accountService = new AccountService(accountRepository, restTemplate, meterRegistry);
        ReflectionTestUtils.setField(accountService, "notificationServiceUrl",
                "http://notification-service:8084");
    }

    private Account account(String number, Long userId, String holder, Account.AccountStatus status,
                            String balance) {
        return Account.builder()
                .id(1L)
                .accountNumber(number)
                .userId(userId)
                .accountHolderName(holder)
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal(balance))
                .status(status)
                .build();
    }

    private CreateAccountRequest createRequest() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(Account.AccountType.SAVINGS);
        request.setAccountHolderName("Alice Smith");
        return request;
    }

    private BalanceUpdateRequest balanceRequest(BalanceUpdateRequest.OperationType type, String amount) {
        BalanceUpdateRequest request = new BalanceUpdateRequest();
        request.setOperationType(type);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    // ----------------------------------------------------------------
    //  createAccount
    // ----------------------------------------------------------------

    @Test
    void createAccountGeneratesUniqueNumberAndPersists() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AccountResponse response = accountService.createAccount(5L, createRequest());

        assertThat(response.getAccountNumber()).startsWith("ACC").hasSize(15);
        assertThat(response.getUserId()).isEqualTo(5L);
        assertThat(response.getAccountHolderName()).isEqualTo("Alice Smith");
        assertThat(response.getAccountType()).isEqualTo("SAVINGS");
        assertThat(response.getBalance()).isEqualByComparingTo("0");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(meterRegistry.counter("banking.account.creations").count()).isEqualTo(1);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccountRegeneratesNumberOnCollision() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(true, false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.createAccount(5L, createRequest());

        assertThat(response.getAccountNumber()).startsWith("ACC").hasSize(15);
        verify(accountRepository, never()).save(null);
    }

    // ----------------------------------------------------------------
    //  Query methods
    // ----------------------------------------------------------------

    @Test
    void getAccountsByUserIdMapsAllAccounts() {
        when(accountRepository.findByUserId(5L))
                .thenReturn(List.of(
                        account("ACC100000000001", 5L, "Alice Smith", Account.AccountStatus.ACTIVE, "100.00"),
                        account("ACC100000000002", 5L, "Alice Smith", Account.AccountStatus.ACTIVE, "200.00")));

        List<AccountResponse> responses = accountService.getAccountsByUserId(5L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getAccountNumber()).isEqualTo("ACC100000000001");
        assertThat(responses.get(1).getBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void getAccountByIdReturnsAccountWhenFound() {
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "100.00")));

        AccountResponse response = accountService.getAccountById(1L);

        assertThat(response.getAccountNumber()).isEqualTo("ACC100000000001");
    }

    @Test
    void getAccountByIdThrowsWhenNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(99L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getAccountByNumberReturnsAccountWhenFound() {
        when(accountRepository.findByAccountNumber("ACC100000000001"))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "100.00")));

        AccountResponse response = accountService.getAccountByNumber("ACC100000000001");

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getAccountByNumberThrowsWhenNotFound() {
        when(accountRepository.findByAccountNumber("ACC999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountByNumber("ACC999999999999"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ----------------------------------------------------------------
    //  updateBalance
    // ----------------------------------------------------------------

    @Test
    void creditOperationAddsAmount() {
        when(accountRepository.findByAccountNumberWithLock("ACC100000000001"))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "100.00")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.updateBalance("ACC100000000001",
                balanceRequest(BalanceUpdateRequest.OperationType.CREDIT, "50"));

        assertThat(response.getBalance()).isEqualByComparingTo("150.00");
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void debitOperationSubtractsAmountWithoutNotificationWhenAboveThreshold() {
        when(accountRepository.findByAccountNumberWithLock("ACC100000000001"))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "1000.00")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.updateBalance("ACC100000000001",
                balanceRequest(BalanceUpdateRequest.OperationType.DEBIT, "100"));

        assertThat(response.getBalance()).isEqualByComparingTo("900.00");
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void debitOperationSendsLowBalanceNotificationWhenBelowThreshold() {
        when(accountRepository.findByAccountNumberWithLock("ACC100000000001"))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "600.00")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.updateBalance("ACC100000000001",
                balanceRequest(BalanceUpdateRequest.OperationType.DEBIT, "200"));

        assertThat(response.getBalance()).isEqualByComparingTo("400.00");
        verify(restTemplate).postForEntity(
                org.mockito.ArgumentMatchers.contains("/api/notifications/internal"), any(), any());
    }

    @Test
    void debitOperationStillSucceedsWhenNotificationFails() {
        when(accountRepository.findByAccountNumberWithLock("ACC100000000001"))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "600.00")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(restTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(new RuntimeException("notification service down"));

        AccountResponse response = accountService.updateBalance("ACC100000000001",
                balanceRequest(BalanceUpdateRequest.OperationType.DEBIT, "200"));

        assertThat(response.getBalance()).isEqualByComparingTo("400.00");
    }

    @Test
    void debitOperationRejectsInsufficientFunds() {
        when(accountRepository.findByAccountNumberWithLock("ACC100000000001"))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "50.00")));

        assertThatThrownBy(() -> accountService.updateBalance("ACC100000000001",
                balanceRequest(BalanceUpdateRequest.OperationType.DEBIT, "100")))
                .isInstanceOf(InsufficientFundsException.class);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updateBalanceRejectsInactiveAccount() {
        when(accountRepository.findByAccountNumberWithLock("ACC100000000001"))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.INACTIVE, "100.00")));

        assertThatThrownBy(() -> accountService.updateBalance("ACC100000000001",
                balanceRequest(BalanceUpdateRequest.OperationType.CREDIT, "10")))
                .isInstanceOf(InactiveAccountException.class);
    }

    // ----------------------------------------------------------------
    //  closeAccount
    // ----------------------------------------------------------------

    @Test
    void closeAccountMarksStatusClosed() {
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account("ACC100000000001", 5L, "Alice Smith",
                        Account.AccountStatus.ACTIVE, "100.00")));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.closeAccount(1L);

        assertThat(response.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void closeAccountThrowsWhenNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.closeAccount(99L))
                .isInstanceOf(AccountNotFoundException.class);
    }
}