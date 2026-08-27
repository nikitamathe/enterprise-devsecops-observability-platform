package com.banking.transaction.controller;

import com.banking.transaction.dto.TransactionResponse;
import com.banking.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json()
                         .modules(
                                new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule(),
                                new org.springframework.data.web.config.SpringDataJacksonConfiguration.PageModule(
                                        new org.springframework.data.web.config.SpringDataWebSettings(
                                                org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)))
                        .build();
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new com.banking.transaction.exception.GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private TransactionResponse transactionResponse() {
        return TransactionResponse.builder()
                .id(1L)
                .transactionReference("TXN-reference")
                .userId(7L)
                .transactionType("DEPOSIT")
                .amount(new BigDecimal("50.00"))
                .balanceBefore(new BigDecimal("100.00"))
                .balanceAfter(new BigDecimal("150.00"))
                .status("SUCCESS")
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    private org.springframework.data.domain.Page<TransactionResponse> page() {
        return new PageImpl<>(List.of(transactionResponse()));
    }

    @Test
    void depositReturnsCreated() throws Exception {
        when(transactionService.deposit(anyLong(), any())).thenReturn(transactionResponse());

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content("{\"transactionType\":\"DEPOSIT\",\"amount\":50,"
                                + "\"accountNumber\":\"ACC100000000001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Deposit successful"))
                .andExpect(jsonPath("$.data.transactionReference").value("TXN-reference"));
    }

    @Test
    void withdrawReturnsCreated() throws Exception {
        when(transactionService.withdraw(anyLong(), any())).thenReturn(transactionResponse());

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content("{\"transactionType\":\"WITHDRAWAL\",\"amount\":40,"
                                + "\"accountNumber\":\"ACC100000000001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Withdrawal successful"));
    }

    @Test
    void transferReturnsCreated() throws Exception {
        when(transactionService.transfer(anyLong(), any())).thenReturn(transactionResponse());

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content("{\"transactionType\":\"TRANSFER\",\"amount\":50,"
                                + "\"fromAccountNumber\":\"ACC100000000001\","
                                + "\"toAccountNumber\":\"ACC100000000002\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Transfer successful"));
    }

    @Test
    void getMyTransactionsReturnsPage() throws Exception {
        when(transactionService.getTransactionsByUserId(7L, 0, 20)).thenReturn(page());

        mockMvc.perform(get("/api/transactions").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"));
    }

    @Test
    void getByAccountReturnsPage() throws Exception {
        when(transactionService.getTransactionsByAccount("ACC100000000001", 0, 20)).thenReturn(page());

        mockMvc.perform(get("/api/transactions/account/ACC100000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getByReferenceReturnsTransaction() throws Exception {
        when(transactionService.getTransactionByReference("TXN-reference"))
                .thenReturn(transactionResponse());

        mockMvc.perform(get("/api/transactions/reference/TXN-reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionReference").value("TXN-reference"));
    }

    @Test
    void getHistoryReturnsPageForValidRange() throws Exception {
        when(transactionService.getTransactionsByUserIdAndDateRange(anyLong(), any(), any(),
                anyInt(), anyInt())).thenReturn(page());

        mockMvc.perform(get("/api/transactions/history")
                        .header("X-User-Id", "7")
                        .param("from", "2025-01-01T00:00:00")
                        .param("to", "2025-01-31T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].message").doesNotExist());
    }

    @Test
    void getHistoryRejectsInvertedRange() throws Exception {
        mockMvc.perform(get("/api/transactions/history")
                        .header("X-User-Id", "7")
                        .param("from", "2025-02-01T00:00:00")
                        .param("to", "2025-01-01T00:00:00"))
                .andExpect(status().isBadRequest());

        verify(transactionService, never())
                .getTransactionsByUserIdAndDateRange(anyLong(), any(), any(), anyInt(), anyInt());
    }
}