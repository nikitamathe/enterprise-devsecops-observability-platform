package com.banking.common.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for masking personally identifiable information in log output.
 *
 * <p>Account numbers (<code>ACC</code> followed by 12 digits) are partially masked
 * (prefix + first 4 and last 4 digits kept). Usernames are replaced with a stable
 * SHA-256 hash so activity can still be correlated across logs without exposing
 * the raw username.</p>
 */
public final class PiiSanitizer {

    public static final String ACCOUNT_NUMBER_PATTERN = "ACC[0-9]{12}";
    private static final Pattern ACCOUNT_NUMBER_REGEX = Pattern.compile(ACCOUNT_NUMBER_PATTERN);
    private static final int VISIBLE_DIGITS = 4;
    private static final String MASK = "****";
    private static final String USERNAME_HASH_PREFIX = "u_";
    private static final int USERNAME_HASH_LENGTH = 16;

    private PiiSanitizer() {
    }

    /**
     * Masks a single account number, keeping the <code>ACC</code> prefix plus the
     * first and last 4 digits, e.g. <code>ACC123456789012</code> becomes
     * <code>ACC1234****9012</code>. Non-matching (or already masked) values are
     * returned unchanged, so repeated masking is safe.
     */
    public static String maskAccountNumber(String value) {
        if (value == null || !ACCOUNT_NUMBER_REGEX.matcher(value).matches()) {
            return value;
        }
        return value.substring(0, 3 + VISIBLE_DIGITS) + MASK
                + value.substring(value.length() - VISIBLE_DIGITS);
    }

    /**
     * Masks every account number found inside an arbitrary text (log messages,
     * exception messages, paths). Returns {@code null} for {@code null} input.
     */
    public static String maskAccountNumbers(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = ACCOUNT_NUMBER_REGEX.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        matcher.reset();
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(maskAccountNumber(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String maskAccountNumbers(Object value) {
        return value == null ? null : maskAccountNumbers(String.valueOf(value));
    }

    /**
     * Replaces a username with a stable SHA-256 hash (prefix <code>u_</code>, 16 hex
     * chars) so log lines referencing the same user can be joined without exposing
     * the raw username.
     */
    public static String hashUsername(String username) {
        if (username == null || username.isEmpty()) {
            return username;
        }
        String hex = HexFormat.of().formatHex(sha256(username));
        return USERNAME_HASH_PREFIX + hex.substring(0, USERNAME_HASH_LENGTH);
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}