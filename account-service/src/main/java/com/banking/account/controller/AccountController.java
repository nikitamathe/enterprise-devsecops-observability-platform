package com.banking.account.controller;

import com.banking.account.dto.AccountResponse;
import com.banking.common.dto.ApiResponse;
import com.banking.account.dto.BalanceUpdateRequest;
import com.banking.account.dto.CreateAccountRequest;
import com.banking.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = accountService.createAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", account));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
            @RequestHeader("X-User-Id") Long userId) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Accounts retrieved", accounts));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable Long accountId) {
        AccountResponse account = accountService.getAccountById(accountId);
        return ResponseEntity.ok(ApiResponse.success("Account retrieved", account));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByNumber(
            @PathVariable String accountNumber) {
        AccountResponse account = accountService.getAccountByNumber(accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account retrieved", account));
    }

    @PatchMapping("/{accountNumber}/balance")
    public ResponseEntity<ApiResponse<AccountResponse>> updateBalance(
            @PathVariable String accountNumber,
            @Valid @RequestBody BalanceUpdateRequest request) {
        AccountResponse account = accountService.updateBalance(accountNumber, request);
        return ResponseEntity.ok(ApiResponse.success("Balance updated", account));
    }

    @DeleteMapping("/{accountId}/close")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(
            @PathVariable Long accountId) {
        AccountResponse account = accountService.closeAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success("Account closed", account));
    }
}
