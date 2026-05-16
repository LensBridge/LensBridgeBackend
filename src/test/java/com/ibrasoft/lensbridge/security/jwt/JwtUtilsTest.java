package com.ibrasoft.lensbridge.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

    // 64 random bytes, base64-encoded -> valid HS256 key material.
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789012345678901234567890123456789012345678901234567890123".getBytes());

    @Mock
    private Authentication authentication;

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 60_000L);
        lenient().when(authentication.getName()).thenReturn("user@example.com");
    }

    @Test
    void generateJwtTokenProducesParseableToken() {
        String token = jwtUtils.generateJwtToken(authentication);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void getUserNameFromJwtTokenReturnsSubject() {
        String token = jwtUtils.generateJwtToken(authentication);

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("user@example.com");
    }

    @Test
    void validateJwtTokenAcceptsValidToken() {
        String token = jwtUtils.generateJwtToken(authentication);

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
    }

    @Test
    void validateJwtTokenRejectsExpiredToken() throws InterruptedException {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 1L);
        String token = jwtUtils.generateJwtToken(authentication);
        Thread.sleep(10);

        assertThat(jwtUtils.validateJwtToken(token)).isFalse();
    }

    @Test
    void validateJwtTokenRejectsMalformedToken() {
        assertThat(jwtUtils.validateJwtToken("not-a-jwt")).isFalse();
    }

    @Test
    void validateJwtTokenRejectsTokenSignedWithDifferentKey() {
        JwtUtils other = new JwtUtils();
        ReflectionTestUtils.setField(other, "jwtSecret",
                Base64.getEncoder().encodeToString(
                        "abcdefghijklmnopabcdefghijklmnopabcdefghijklmnopabcdefghijklmnop".getBytes()));
        ReflectionTestUtils.setField(other, "jwtExpirationMs", 60_000L);
        String foreignToken = other.generateJwtToken(authentication);

        assertThat(jwtUtils.validateJwtToken(foreignToken)).isFalse();
    }

    @Test
    void validateJwtTokenRejectsEmptyToken() {
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }

    @Test
    void validateJwtTokenRejectsNullToken() {
        assertThat(jwtUtils.validateJwtToken(null)).isFalse();
    }

    @Test
    void getUserNameFromExpiredTokenThrows() throws InterruptedException {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 1L);
        String token = jwtUtils.generateJwtToken(authentication);
        Thread.sleep(10);

        assertThatThrownBy(() -> jwtUtils.getUserNameFromJwtToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
