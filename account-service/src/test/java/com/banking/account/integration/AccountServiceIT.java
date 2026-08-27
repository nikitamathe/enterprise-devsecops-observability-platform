package com.banking.account.integration;

import com.banking.account.dto.AccountResponse;
import com.banking.account.dto.BalanceUpdateRequest;
import com.banking.account.dto.CreateAccountRequest;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.InsufficientFundsException;
import com.banking.account.model.Account;
import com.banking.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=VGhpcyBJcyBBIExvbmcgU2VjcmV0IEtleSBGb3IgSmFzb24gMzIgU2lnbmF0dXJl",
        "notification-service.url=http://localhost:8084"
})
class AccountServiceIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("banking_db");

    @Autowired
    private AccountService accountService;

    private CreateAccountRequest requestFor(String holderName) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(Account.AccountType.SAVINGS);
        request.setAccountHolderName(holderName);
        return request;
    }

    @Test
    void createAccountPersistsAccountWithZeroBalance() {
        AccountResponse created = accountService.createAccount(7L, requestFor("Alice Smith"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getUserId()).isEqualTo(7L);
        assertThat(created.getAccountHolderName()).isEqualTo("Alice Smith");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.getAccountNumber()).isNotBlank();
    }

    @Test
    void createAccountGeneratesDistinctAccountNumbers() {
        AccountResponse first = accountService.createAccount(7L, requestFor("Alice Smith"));
        AccountResponse second = accountService.createAccount(7L, requestFor("Bob Jones"));

        assertThat(second.getAccountNumber()).isNotEqualTo(first.getAccountNumber());
    }

    @Test
    void accountCanBeRetrievedByNumber() {
        AccountResponse created = accountService.createAccount(7L, requestFor("Alice Smith"));

        AccountResponse fetched = accountService.getAccountByNumber(created.getAccountNumber());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void accountsCanBeListedByUser() {
        accountService.createAccount(107L, requestFor("Alice Smith"));
        accountService.createAccount(107L, requestFor("Bob Jones"));

        List<AccountResponse> accounts = accountService.getAccountsByUserId(107L);

        assertThat(accounts).hasSize(2);
    }

    @Test
    void accountCanBeRetrievedById() {
        AccountResponse created = accountService.createAccount(7L, requestFor("Alice Smith"));

        AccountResponse fetched = accountService.getAccountById(created.getId());

        assertThat(fetched.getAccountNumber()).isEqualTo(created.getAccountNumber());
    }

    @Test
    void getAccountByNumberThrowsWhenNotFound() {
        assertThatThrownBy(() -> accountService.getAccountByNumber("ACC999999999999"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void creditAndDebitUpdatePersistedBalance() {
        AccountResponse created = accountService.createAccount(7L, requestFor("Alice Smith"));
        BalanceUpdateRequest credit = new BalanceUpdateRequest();
        credit.setAmount(new BigDecimal("100.00"));
        credit.setOperationType(BalanceUpdateRequest.OperationType.CREDIT);
        BalanceUpdateRequest debit = new BalanceUpdateRequest();
        debit.setAmount(new BigDecimal("40.00"));
        debit.setOperationType(BalanceUpdateRequest.OperationType.DEBIT);

        AccountResponse afterCredit = accountService.updateBalance(created.getAccountNumber(), credit);
        AccountResponse afterDebit = accountService.updateBalance(created.getAccountNumber(), debit);

        assertThat(afterCredit.getBalance()).isEqualByComparingTo("100.00");
        assertThat(afterDebit.getBalance()).isEqualByComparingTo("60.00");

        AccountResponse reloaded = accountService.getAccountByNumber(created.getAccountNumber());
        assertThat(reloaded.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitWithInsufficientFundsThrows() {
        AccountResponse created = accountService.createAccount(7L, requestFor("Alice Smith"));
        BalanceUpdateRequest debit = new BalanceUpdateRequest();
        debit.setAmount(new BigDecimal("10.00"));
        debit.setOperationType(BalanceUpdateRequest.OperationType.DEBIT);

        assertThatThrownBy(() -> accountService.updateBalance(created.getAccountNumber(), debit))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void closeAccountMarksItClosed() {
        AccountResponse created = accountService.createAccount(7L, requestFor("Alice Smith"));

        AccountResponse closed = accountService.closeAccount(created.getId());

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        AccountResponse reloaded = accountService.getAccountByNumber(created.getAccountNumber());
        assertThat(reloaded.getStatus()).isEqualTo("CLOSED");
    }
}