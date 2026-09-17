package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class Ed25519VerifierTest {

    private Ed25519Verifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new Ed25519Verifier();
    }

    @Test
    void verifiesGenuineSignature() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] message = "musallahboard-auth-v1\nsess\nchallenge\ndev\n123".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(message);
        byte[] sig = signer.sign();

        byte[] rawPub = extractRawPublicKey(kp.getPublic());
        assertTrue(verifier.verify(rawPub, message, sig));
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(message);
        byte[] sig = signer.sign();
        sig[0] ^= 0x01;

        byte[] rawPub = extractRawPublicKey(kp.getPublic());
        assertFalse(verifier.verify(rawPub, message, sig));
    }

    @Test
    void rejectsSignatureWithDifferentKey() throws Exception {
        KeyPair signing = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signing.getPrivate());
        signer.update(message);
        byte[] sig = signer.sign();

        byte[] otherPub = extractRawPublicKey(other.getPublic());
        assertFalse(verifier.verify(otherPub, message, sig));
    }

    @Test
    void rejectsMalformedInputs() {
        assertFalse(verifier.verify(null, new byte[0], new byte[64]));
        assertFalse(verifier.verify(new byte[31], new byte[0], new byte[64]));
        assertFalse(verifier.verify(new byte[32], null, new byte[64]));
        assertFalse(verifier.verify(new byte[32], new byte[0], new byte[63]));
    }

    /**
     * JDK exposes Ed25519 public keys via EdECPublicKey.getPoint(); we need the raw 32-byte
     * little-endian encoding the wire format uses.
     */
    private static byte[] extractRawPublicKey(PublicKey publicKey) {
        EdECPublicKey ed = (EdECPublicKey) publicKey;
        byte[] y = ed.getPoint().getY().toByteArray();
        // BigInteger#toByteArray is big-endian; reverse and pad to 32 bytes little-endian.
        byte[] le = new byte[32];
        for (int i = 0; i < y.length && i < 32; i++) {
            le[i] = y[y.length - 1 - i];
        }
        if (ed.getPoint().isXOdd()) {
            le[31] |= (byte) 0x80;
        }
        return le;
    }

    @Test
    void rawKeyEncodingRoundTrips() throws Exception {
        // Belt-and-suspenders: a few iterations to catch any padding / endianness mistakes.
        for (int i = 0; i < 10; i++) {
            KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] raw = extractRawPublicKey(kp.getPublic());
            assertEquals(32, raw.length);
            byte[] msg = ("iter-" + i).getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(kp.getPrivate());
            signer.update(msg);
            byte[] sig = signer.sign();
            assertTrue(verifier.verify(raw, msg, sig), "iteration " + i);
            // also confirm raw is not all-zeros
            assertFalse(Arrays.equals(raw, new byte[32]));
        }
    }
}
