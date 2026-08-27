package com.banking.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractJsonProvider;

import java.io.IOException;

/**
 * JSON provider for the Logstash encoder that writes the {@code app} field (from the
 * {@code spring.application.name} logback context property) and the {@code message}
 * field with all account numbers masked — defense-in-depth for PII that is embedded in
 * log messages (including exception text) outside the explicit log call sites.
 */
public class PiiMaskingJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

    public static final String FIELD_APP = "app";
    public static final String FIELD_MESSAGE = "message";

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        Object app = getContext() == null ? null : getContext().getProperty(FIELD_APP);
        if (app != null) {
            generator.writeStringField(FIELD_APP, String.valueOf(app));
        }
        generator.writeStringField(FIELD_MESSAGE, PiiSanitizer.maskAccountNumbers(event.getFormattedMessage()));
    }
}