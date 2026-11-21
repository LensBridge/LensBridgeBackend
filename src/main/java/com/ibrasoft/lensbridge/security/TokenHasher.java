package com.ibrasoft.lensbridge.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex hashing for bearer-style secrets that are stored server-side (refresh tokens,
 * email verification and password reset tokens). Only the hash is persisted, so a leaked
 * database row or backup cannot be replayed as a credential. The inputs carry 256 bits or more
 * of randomness, so a fast unsalted hash is sufficient (there is nothing to brute-force).
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
