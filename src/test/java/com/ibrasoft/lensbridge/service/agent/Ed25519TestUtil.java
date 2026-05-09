package com.ibrasoft.lensbridge.service.agent;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;

/**
 * Shared helpers for tests that need to generate Ed25519 keypairs and produce
 * raw 32-byte public-key bytes matching the on-the-wire format.
 */
public final class Ed25519TestUtil {

    private Ed25519TestUtil() {}

    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] rawPublicKey(PublicKey publicKey) {
        EdECPublicKey ed = (EdECPublicKey) publicKey;
        byte[] y = ed.getPoint().getY().toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < y.length && i < 32; i++) {
            le[i] = y[y.length - 1 - i];
        }
        if (ed.getPoint().isXOdd()) {
            le[31] |= (byte) 0x80;
        }
        return le;
    }

    public static byte[] sign(PrivateKey privateKey, byte[] message) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(message);
            return signer.sign();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
