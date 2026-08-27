package com.banking.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.LogstashVersionJsonProvider;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LogLevelValueJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggerNameJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventThreadNameJsonProvider;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingJsonProviderTest {

    @Test
    void writeToMasksAccountNumbersInMessage() throws Exception {
        PiiMaskingJsonProvider provider = new PiiMaskingJsonProvider();
        LoggingEvent event = new LoggingEvent();
        event.setTimeStamp(System.currentTimeMillis());
        event.setMessage("Created account ACC123456789012 for user bob");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(out);
        generator.writeStartObject();
        provider.writeTo(generator, event);
        generator.writeEndObject();
        generator.flush();

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"message\":\"Created account ACC1234****9012 for user bob\"");
        assertThat(json).doesNotContain("ACC123456789012");
    }

    @Test
    void productionProviderSetMasksMessageInEncodedJson() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putProperty("app", "test-app");

        LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
        encoder.setContext(context);

        LoggingEventJsonProviders providers = (LoggingEventJsonProviders) encoder.getProviders();
        providers.addTimestamp(new LoggingEventFormattedTimestampJsonProvider());
        providers.addVersion(new LogstashVersionJsonProvider<>());
        providers.addLogLevel(new LogLevelJsonProvider());
        providers.addLogLevelValue(new LogLevelValueJsonProvider());
        providers.addLoggerName(new LoggerNameJsonProvider());
        providers.addThreadName(new LoggingEventThreadNameJsonProvider());
        providers.addProvider(new PiiMaskingJsonProvider());
        MdcJsonProvider mdc = new MdcJsonProvider();
        mdc.setIncludeMdcKeyNames(List.of("correlationId", "traceId", "spanId"));
        providers.addMdc(mdc);
        StackTraceJsonProvider stackTrace = new StackTraceJsonProvider();
        stackTrace.setThrowableConverter(new PiiMaskingThrowableConverter());
        providers.addStackTrace(stackTrace);
        encoder.start();

        LoggingEvent event = new LoggingEvent("test.fqcn",
                context.getLogger("com.banking.test"), Level.INFO,
                "Transfer ACC123456789012 -> ACC246813579024 ok", null, null);
        event.setTimeStamp(System.currentTimeMillis());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        encoder.encode(event, buffer);

        String json = buffer.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .doesNotContain("ACC123456789012", "ACC246813579024")
                .contains("ACC1234****9012", "ACC2468****9024")
                .contains("\"level\":\"INFO\"", "\"logger_name\"", "\"app\":\"test-app\"")
                .doesNotContain("\"context\"");
        encoder.stop();
    }
}