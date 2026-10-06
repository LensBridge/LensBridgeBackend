package com.ibrasoft.lensbridge.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "fedcba9876543210fedcba9876543210fedcba9876543210".getBytes());

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 60_000L);
    }

    @Test
    void generatedTokenValidatesAndCarriesTheUsername() {
        String token = jwtUtils.generateJwtToken("user@example.com");

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("user@example.com");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejectedWithoutThrowing() {
        String forged = Jwts.builder().setSubject("user@example.com")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(OTHER_SECRET)), SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtils.validateJwtToken(forged)).isFalse();
    }

    @Test
    void expiredMalformedAndEmptyTokensAreRejected() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1_000L);
        String expired = jwtUtils.generateJwtToken("user@example.com");

        assertThat(jwtUtils.validateJwtToken(expired)).isFalse();
        assertThat(jwtUtils.validateJwtToken("not-a-jwt")).isFalse();
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }
}
