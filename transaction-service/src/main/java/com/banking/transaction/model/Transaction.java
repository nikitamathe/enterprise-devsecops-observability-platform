package com.banking.transaction.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 30)
    private String transactionReference;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "from_account_number", length = 20)
    private String fromAccountNumber;

    @Column(name = "to_account_number", length = 20)
    private String toAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    @Column(precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(length = 255)
    private String description;

    @Column(length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER;

        @JsonCreator
        public static TransactionType fromValue(String value) {
            if (value == null) {
                return null;
            }
            return switch (value.trim().toUpperCase()) {
                case "WITHDRAW" -> WITHDRAWAL;
                case "DEPOSIT" -> DEPOSIT;
                case "TRANSFER" -> TRANSFER;
                default -> TransactionType.valueOf(value.trim().toUpperCase());
            };
        }

        @JsonValue
        public String toValue() {
            return this.name();
        }
    }

    public enum TransactionStatus {
        PENDING, SUCCESS, FAILED
    }
}
