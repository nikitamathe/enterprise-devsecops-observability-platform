package com.banking.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes audit entries for sensitive operations (login, balance changes, transfers)
 * to a dedicated {@code AUDIT} logger so they are emitted as structured JSON with a
 * single {@code logger_name} that log aggregation can pick up independently.
 *
 * <p>Account numbers embedded in the actor, resource, or details are masked before
 * they reach the log output (PII-safe audit trail). The {@code correlationId} MDC
 * field is attached automatically by the logback configuration when the entry is
 * written inside a request context.</p>
 */
public final class AuditLogger {

    public static final String LOGGER_NAME = "AUDIT";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    private AuditLogger() {
    }

    public static void success(String action, String actor, String resource, String details) {
        audit(action, actor, resource, OUTCOME_SUCCESS, details);
    }

    public static void failure(String action, String actor, String resource, String details) {
        audit(action, actor, resource, OUTCOME_FAILURE, details);
    }

    public static void audit(String action, String actor, String resource, String outcome, String details) {
        String safeActor = PiiSanitizer.maskAccountNumbers(actor);
        String safeResource = PiiSanitizer.maskAccountNumbers(resource);
        String safeDetails = PiiSanitizer.maskAccountNumbers(details);
        LOG.info("action={} actor={} resource={} outcome={} details={}",
                String.valueOf(action), safeActor, safeResource, outcome, safeDetails);
    }
}