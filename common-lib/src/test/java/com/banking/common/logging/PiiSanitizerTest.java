package com.banking.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiSanitizerTest {

    @Test
    void maskAccountNumberMasksMiddleDigits() {
        assertThat(PiiSanitizer.maskAccountNumber("ACC123456789012"))
                .isEqualTo("ACC1234****9012");
    }

    @Test
    void maskAccountNumberLeavesNonMatchingValuesUntouched() {
        assertThat(PiiSanitizer.maskAccountNumber("ACC-1")).isEqualTo("ACC-1");
        assertThat(PiiSanitizer.maskAccountNumber("acc123456789012")).isEqualTo("acc123456789012");
        assertThat(PiiSanitizer.maskAccountNumber(null)).isNull();
    }

    @Test
    void maskAccountNumbersMasksAllOccurrencesInText() {
        String input = "Deposit failed for account ACC123456789012: " +
                "Account not found: ACC987654321098";
        String masked = PiiSanitizer.maskAccountNumbers(input);
        assertThat(masked)
                .contains("ACC1234****9012", "ACC9876****1098")
                .doesNotContain("ACC123456789012", "ACC987654321098");
    }

    @Test
    void maskAccountNumbersIsIdempotent() {
        String masked = PiiSanitizer.maskAccountNumbers("account ACC123456789012 failed");
        assertThat(PiiSanitizer.maskAccountNumbers(masked)).isEqualTo(masked);
    }

    @Test
    void maskAccountNumbersHandlesNullAndEmpty() {
        assertThat(PiiSanitizer.maskAccountNumbers((String) null)).isNull();
        assertThat(PiiSanitizer.maskAccountNumbers("")).isEmpty();
    }

    @Test
    void hashUsernameIsStableAndNotRaw() {
        String first = PiiSanitizer.hashUsername("alice@example.com");
        String second = PiiSanitizer.hashUsername("alice@example.com");
        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("u_").doesNotContain("alice");
        assertThat(first).hasSize(2 + 16);
        assertThat(PiiSanitizer.hashUsername("bob")).isNotEqualTo(first);
    }

    @Test
    void hashUsernameHandlesNullAndEmpty() {
        assertThat(PiiSanitizer.hashUsername(null)).isNull();
        assertThat(PiiSanitizer.hashUsername("")).isEmpty();
    }
}