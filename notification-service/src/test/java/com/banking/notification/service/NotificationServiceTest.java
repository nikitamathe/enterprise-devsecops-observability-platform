package com.banking.notification.service;

import com.banking.notification.dto.NotificationRequest;
import com.banking.notification.dto.NotificationResponse;
import com.banking.notification.exception.ForbiddenException;
import com.banking.notification.exception.ResourceNotFoundException;
import com.banking.notification.model.Notification;
import com.banking.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository);
    }

    private NotificationRequest request(String type) {
        NotificationRequest request = new NotificationRequest();
        request.setType(type);
        request.setUserId(7L);
        request.setAccountNumber("ACC100000000001");
        request.setTransactionReference("TXN-reference");
        request.setAmount(new BigDecimal("100.00"));
        request.setBalance(new BigDecimal("900.00"));
        return request;
    }

    private Notification notification(Long id, Long userId, boolean read) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type(Notification.NotificationType.DEPOSIT_SUCCESS)
                .message("message")
                .accountNumber("ACC100000000001")
                .amount(new BigDecimal("100.00"))
                .balance(new BigDecimal("900.00"))
                .read(read)
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    private void saveReturnsItsArgument() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createNotificationBuildsDepositMessage() {
        saveReturnsItsArgument();

        NotificationResponse response = service.createNotification(request("DEPOSIT_SUCCESS"));

        assertThat(response.getMessage())
                .contains("Deposit of 100.00")
                .contains("ACC100000000001")
                .contains("900.00");
        assertThat(response.getUserId()).isEqualTo(7L);
    }

    @Test
    void createNotificationBuildsWithdrawalMessage() {
        saveReturnsItsArgument();

        NotificationResponse response = service.createNotification(request("WITHDRAWAL_SUCCESS"));

        assertThat(response.getMessage()).contains("Withdrawal of 100.00");
    }

    @Test
    void createNotificationBuildsLowBalanceMessage() {
        saveReturnsItsArgument();

        NotificationResponse response = service.createNotification(request("LOW_BALANCE"));

        assertThat(response.getMessage())
                .contains("Low balance alert")
                .contains("ACC100000000001")
                .contains("900.00");
    }

    @Test
    void createNotificationBuildsAccountCreatedMessage() {
        saveReturnsItsArgument();

        NotificationResponse response = service.createNotification(request("ACCOUNT_CREATED"));

        assertThat(response.getMessage())
                .contains("has been created successfully")
                .contains("ACC100000000001");
    }

    @Test
    void createNotificationBuildsTransferMessage() {
        saveReturnsItsArgument();

        NotificationResponse response = service.createNotification(request("TRANSFER_SUCCESS"));

        assertThat(response.getMessage()).contains("Transfer of 100.00");
    }

    @Test
    void createNotificationRejectsUnknownType() {
        assertThatThrownBy(() -> service.createNotification(request("SPAM")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void getNotificationsByUserIdMapsPage() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(1L, 7L, false))));

        Page<NotificationResponse> result = service.getNotificationsByUserId(7L, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(7L);
    }

    @Test
    void getUnreadNotificationsReturnsFilteredList() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(notification(1L, 7L, false), notification(2L, 7L, false)));

        List<NotificationResponse> result = service.getUnreadNotifications(7L);

        assertThat(result).hasSize(2);
    }

    @Test
    void getUnreadCountReturnsCount() {
        when(notificationRepository.countByUserIdAndReadFalse(7L)).thenReturn(3L);

        assertThat(service.getUnreadCount(7L)).isEqualTo(3L);
    }

    @Test
    void markAsReadMarksNotificationWhenBelongsToUser() {
        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification(1L, 7L, false)));
        saveReturnsItsArgument();

        service.markAsRead(1L, 7L);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markAsReadThrowsWhenNotificationNotFound() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(99L, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAsReadThrowsWhenBelongsToAnotherUser() {
        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification(1L, 8L, false)));

        assertThatThrownBy(() -> service.markAsRead(1L, 7L))
                .isInstanceOf(ForbiddenException.class);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAllAsReadCallsRepository() {
        when(notificationRepository.markAllAsRead(7L)).thenReturn(5);

        service.markAllAsRead(7L);

        verify(notificationRepository).markAllAsRead(7L);
    }
}