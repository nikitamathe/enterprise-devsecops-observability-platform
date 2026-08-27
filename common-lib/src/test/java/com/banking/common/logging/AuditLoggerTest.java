package com.banking.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLoggerTest {

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachListAppender() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
        auditLogger.setAdditive(false);
        appender = new ListAppender<>();
        appender.setContext(auditLogger.getLoggerContext());
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachListAppender() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
        auditLogger.detachAppender(appender);
    }

    @Test
    void successWritesStructuredAuditEntryWithoutRawAccountNumber() {
        AuditLogger.success("TRANSFER", "42", "ACC123456789012", "reference=TX-1 amount=100.00");

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .contains("action=TRANSFER", "actor=42", "outcome=SUCCESS", "ACC1234****9012")
                .doesNotContain("ACC123456789012");
    }

    @Test
    void failureWritesOutcomeAndMasksDetails() {
        AuditLogger.failure("BALANCE_CHANGE", "7", "ACC123456789012",
                "Insufficient funds for ACC123456789012");

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .contains("action=BALANCE_CHANGE", "actor=7", "outcome=FAILURE",
                        "ACC1234****9012", "Insufficient funds for ACC1234****9012")
                .doesNotContain("ACC123456789012");
    }

    @Test
    void logsToDedicatedAuditLogger() {
        AuditLogger.success("LOGIN", "u_abcd1234ef5678", "-", "authenticated");

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list.get(0).getLoggerName()).isEqualTo(AuditLogger.LOGGER_NAME);
    }
}