package com.banking.account.security;

/**
 * Thread-local holder for the incoming JWT token.
 * Populated by JwtAuthenticationFilter so outgoing RestTemplate calls
 * can forward the same token to downstream services (e.g. notification-service).
 */
public final class JwtContext {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private JwtContext() {}

    public static void setToken(String token) {
        TOKEN.set(token);
    }

    public static String getToken() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }
}