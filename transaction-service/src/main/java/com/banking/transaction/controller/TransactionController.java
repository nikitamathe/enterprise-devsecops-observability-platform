package com.banking.transaction.controller;

import com.banking.transaction.dto.ApiResponse;
import com.banking.transaction.dto.TransactionRequest;
import com.banking.transaction.dto.TransactionResponse;
import com.banking.transaction.exception.TransactionException;
import com.banking.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse txn = transactionService.deposit(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deposit successful", txn));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse txn = transactionService.withdraw(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Withdrawal successful", txn));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse txn = transactionService.transfer(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transfer successful", txn));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getMyTransactions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransactionResponse> transactions = transactionService.getTransactionsByUserId(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved", transactions));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getByAccount(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransactionResponse> transactions =
                transactionService.getTransactionsByAccount(accountNumber, page, size);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved", transactions));
    }

    @GetMapping("/reference/{reference}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByReference(
            @PathVariable String reference) {
        TransactionResponse txn = transactionService.getTransactionByReference(reference);
        return ResponseEntity.ok(ApiResponse.success("Transaction retrieved", txn));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        if (!from.isBefore(to)) {
            throw new TransactionException("'from' must be before 'to'");
        }
        List<TransactionResponse> transactions =
                transactionService.getTransactionsByUserIdAndDateRange(userId, from, to);
        return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved", transactions));
    }
}
