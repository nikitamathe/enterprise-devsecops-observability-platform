package com.banking.notification.controller;

import com.banking.notification.dto.NotificationRequest;
import com.banking.notification.dto.NotificationResponse;
import com.banking.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .modules(
                        new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule(),
                        new SpringDataJacksonConfiguration.PageModule(
                                new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)))
                .build();
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new com.banking.notification.exception.GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private NotificationResponse notificationResponse() {
        return NotificationResponse.builder()
                .id(1L)
                .userId(7L)
                .type("DEPOSIT_SUCCESS")
                .message("Deposit of 100.00 credited to account ACC100000000001. New balance: 900.00")
                .accountNumber("ACC100000000001")
                .transactionReference("TXN-reference")
                .amount(new BigDecimal("100.00"))
                .balance(new BigDecimal("900.00"))
                .read(false)
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    @Test
    void receiveInternalCreatesNotification() throws Exception {
        when(notificationService.createNotification(any(NotificationRequest.class)))
                .thenReturn(notificationResponse());

        mockMvc.perform(post("/api/notifications/internal")
                        .contentType("application/json")
                        .content("{\"type\":\"DEPOSIT_SUCCESS\",\"userId\":7,"
                                + "\"accountNumber\":\"ACC100000000001\",\"amount\":100,\"balance\":900}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Notification created"))
                .andExpect(jsonPath("$.data.message").value(
                        "Deposit of 100.00 credited to account ACC100000000001. New balance: 900.00"));
    }

    @Test
    void getMyNotificationsReturnsPage() throws Exception {
        when(notificationService.getNotificationsByUserId(7L, 0, 20))
                .thenReturn(new PageImpl<>(List.of(notificationResponse())));

        mockMvc.perform(get("/api/notifications").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].userId").value(7));
    }

    @Test
    void getUnreadReturnsNotifications() throws Exception {
        when(notificationService.getUnreadNotifications(7L))
                .thenReturn(List.of(notificationResponse()));

        mockMvc.perform(get("/api/notifications/unread").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("DEPOSIT_SUCCESS"));
    }

    @Test
    void getUnreadCountReturnsCount() throws Exception {
        when(notificationService.getUnreadCount(7L)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread/count").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    void markAsReadReturnsSuccess() throws Exception {
        mockMvc.perform(patch("/api/notifications/1/read").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification marked as read"));

        verify(notificationService).markAsRead(1L, 7L);
    }

    @Test
    void markAllAsReadReturnsSuccess() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));

        verify(notificationService).markAllAsRead(7L);
    }
}