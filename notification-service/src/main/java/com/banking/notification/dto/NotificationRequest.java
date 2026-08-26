package com.banking.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationRequest {
    @NotBlank(message = "Notification type is required")
    private String type;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    private String transactionReference;
    private BigDecimal amount;
    private BigDecimal balance;
    private String message;
}
