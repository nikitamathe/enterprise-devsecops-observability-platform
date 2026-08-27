package com.banking.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingThrowableConverterTest {

    private PiiMaskingThrowableConverter converter;

    @BeforeEach
    void setUp() {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        converter = new PiiMaskingThrowableConverter();
        converter.setContext(context);
        converter.start();
    }

    @Test
    void masksAccountNumbersInRenderedStackTrace() {
        LoggingEvent event = new LoggingEvent("test.fqcn",
                new LoggerContext().getLogger("com.banking.test"), Level.ERROR,
                "Transfer failed", new RuntimeException("Failed ACC123456789012: ACC246813579024"), null);
        event.setTimeStamp(System.currentTimeMillis());

        String rendered = converter.convert(event);

        assertThat(rendered)
                .contains("ACC1234****9012", "ACC2468****9024")
                .doesNotContain("ACC123456789012", "ACC246813579024");
    }

    @Test
    void returnsEmptyForEventWithoutThrowable() {
        LoggingEvent event = new LoggingEvent("test.fqcn",
                new LoggerContext().getLogger("com.banking.test"), Level.INFO, "No throwable here", null, null);
        event.setTimeStamp(System.currentTimeMillis());

        String rendered = converter.convert(event);

        assertThat(rendered).isEmpty();
    }
}