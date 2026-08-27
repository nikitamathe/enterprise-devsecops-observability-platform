package com.banking.account.controller;

import com.banking.account.dto.AccountResponse;
import com.banking.account.dto.BalanceUpdateRequest;
import com.banking.account.dto.CreateAccountRequest;
import com.banking.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new com.banking.account.exception.GlobalExceptionHandler())
                .build();
    }

    private AccountResponse accountResponse() {
        return AccountResponse.builder()
                .id(1L)
                .accountNumber("ACC100000000001")
                .userId(5L)
                .accountHolderName("Alice Smith")
                .accountType("SAVINGS")
                .balance(new BigDecimal("100.00"))
                .status("ACTIVE")
                .build();
    }

    @Test
    void createAccountReturnsCreated() throws Exception {
        when(accountService.createAccount(eq(5L), any(CreateAccountRequest.class)))
                .thenReturn(accountResponse());

        mockMvc.perform(post("/api/accounts")
                        .header("X-User-Id", "5")
                        .contentType("application/json")
                        .content("{\"accountType\":\"SAVINGS\",\"accountHolderName\":\"Alice Smith\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully"))
                .andExpect(jsonPath("$.data.accountNumber").value("ACC100000000001"));
    }

    @Test
    void getMyAccountsReturnsList() throws Exception {
        when(accountService.getAccountsByUserId(5L)).thenReturn(List.of(accountResponse()));

        mockMvc.perform(get("/api/accounts").header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void getAccountByIdReturnsAccount() throws Exception {
        when(accountService.getAccountById(1L)).thenReturn(accountResponse());

        mockMvc.perform(get("/api/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountHolderName").value("Alice Smith"));
    }

    @Test
    void getAccountByNumberReturnsAccount() throws Exception {
        when(accountService.getAccountByNumber("ACC100000000001")).thenReturn(accountResponse());

        mockMvc.perform(get("/api/accounts/number/ACC100000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value("ACC100000000001"));
    }

    @Test
    void updateBalanceReturnsUpdatedAccount() throws Exception {
        when(accountService.updateBalance(eq("ACC100000000001"), any(BalanceUpdateRequest.class)))
                .thenReturn(accountResponse());

        mockMvc.perform(patch("/api/accounts/ACC100000000001/balance")
                        .contentType("application/json")
                        .content("{\"amount\":50,\"operationType\":\"CREDIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Balance updated"));
    }

    @Test
    void closeAccountReturnsClosedAccount() throws Exception {
        when(accountService.closeAccount(anyLong())).thenReturn(accountResponse());

        mockMvc.perform(delete("/api/accounts/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account closed"));

        verify(accountService).closeAccount(1L);
    }
}