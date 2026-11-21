package com.ibrasoft.lensbridge.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void producesTheKnownSha256HexDigest() {
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
