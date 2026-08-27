package com.banking.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.stacktrace.ShortenedThrowableConverter;

/**
 * Throwable converter that masks account numbers in rendered stack traces before
 * they reach the log output.
 */
public class PiiMaskingThrowableConverter extends ShortenedThrowableConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiSanitizer.maskAccountNumbers(super.convert(event));
    }
}