package com.banking.notification.service;

import com.banking.common.logging.PiiSanitizer;
import com.banking.notification.dto.NotificationRequest;
import com.banking.notification.dto.NotificationResponse;
import com.banking.notification.exception.ForbiddenException;
import com.banking.notification.exception.ResourceNotFoundException;
import com.banking.notification.model.Notification;
import com.banking.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public NotificationResponse createNotification(NotificationRequest request) {
        Notification.NotificationType type = Notification.NotificationType.valueOf(request.getType());
        String message = buildMessage(type, request);

        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(type)
                .message(message)
                .accountNumber(request.getAccountNumber())
                .transactionReference(request.getTransactionReference())
                .amount(request.getAmount())
                .balance(request.getBalance())
                .build();

        notification = notificationRepository.save(notification);
        log.info("Notification created for user {}: {} - {}", request.getUserId(), type,
                PiiSanitizer.maskAccountNumbers(message));

        // Mock: in production this would dispatch email/SMS
        dispatchMock(notification);

        return mapToResponse(notification);
    }

    public Page<NotificationResponse> getNotificationsByUserId(Long userId, int page, int size) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::mapToResponse);
    }

    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository
                .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new ForbiddenException("Notification does not belong to user");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId);
        log.info("Marked {} notifications as read for user {}", updated, userId);
    }

    // ----------------------------------------------------------------
    //  Helpers
    // ----------------------------------------------------------------
    private String buildMessage(Notification.NotificationType type, NotificationRequest req) {
        return switch (type) {
            case DEPOSIT_SUCCESS ->
                String.format("Deposit of %.2f credited to account %s. New balance: %.2f",
                        req.getAmount(), req.getAccountNumber(), req.getBalance());
            case WITHDRAWAL_SUCCESS ->
                String.format("Withdrawal of %.2f debited from account %s. New balance: %.2f",
                        req.getAmount(), req.getAccountNumber(), req.getBalance());
            case TRANSFER_SUCCESS ->
                String.format("Transfer of %.2f sent from account %s. New balance: %.2f",
                        req.getAmount(), req.getAccountNumber(), req.getBalance());
            case LOW_BALANCE ->
                String.format("Low balance alert: account %s balance is %.2f. Please top up.",
                        req.getAccountNumber(), req.getBalance());
            case ACCOUNT_CREATED ->
                String.format("Your new account %s has been created successfully.", req.getAccountNumber());
            case ACCOUNT_CLOSED ->
                String.format("Account %s has been closed.", req.getAccountNumber());
        };
    }

    private void dispatchMock(Notification notification) {
        // Mock dispatch — replace with real email/SMS provider (e.g. SendGrid, Twilio) in production
        log.info("[MOCK NOTIFICATION] Type={} User={} Message={}",
                notification.getType(), notification.getUserId(),
                PiiSanitizer.maskAccountNumbers(notification.getMessage()));
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .type(n.getType().name())
                .message(n.getMessage())
                .accountNumber(n.getAccountNumber())
                .transactionReference(n.getTransactionReference())
                .amount(n.getAmount())
                .balance(n.getBalance())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
