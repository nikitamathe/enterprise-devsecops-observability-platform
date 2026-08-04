package com.banking.transaction.dto;

import com.banking.transaction.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionRequestDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeWithdrawAliasIntoWithdrawalType() throws Exception {
        String payload = "{\"transactionType\":\"WITHDRAW\",\"amount\":12.50,\"accountNumber\":\"ACC-1\"}";

        TransactionRequest request = objectMapper.readValue(payload, TransactionRequest.class);

        assertEquals(Transaction.TransactionType.WITHDRAWAL, request.getTransactionType());
    }
}
