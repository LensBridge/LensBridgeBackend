package com.ibrasoft.lensbridge.service.agent.handshake;

import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * Verifies Ed25519 signatures using the JDK 17+ native provider.
 * <p>
 * Public keys are persisted as the raw 32-byte Ed25519 point. We wrap the raw key
 * into an X.509 SubjectPublicKeyInfo envelope at verification time so JCE can
 * decode it. This keeps the on-the-wire and at-rest formats minimal.
 */
@Component
public class Ed25519Verifier {

    /** ASN.1 DER prefix for an Ed25519 SubjectPublicKeyInfo: AlgorithmIdentifier + BIT STRING header. */
    private static final byte[] X509_PREFIX = new byte[]{
            0x30, 0x2a,                                    // SEQUENCE, length 42
            0x30, 0x05,                                    // SEQUENCE, length 5
            0x06, 0x03, 0x2b, 0x65, 0x70,                  // OID 1.3.101.112 (Ed25519)
            0x03, 0x21, 0x00                               // BIT STRING, length 33, 0 unused bits
    };

    private static final int RAW_PUBLIC_KEY_LEN = 32;
    private static final int RAW_SIGNATURE_LEN = 64;

    /**
     * @param rawPublicKey 32-byte Ed25519 public key
     * @param message      bytes that were signed
     * @param signature    64-byte detached signature
     * @return true iff the signature verifies
     */
    public boolean verify(byte[] rawPublicKey, byte[] message, byte[] signature) {
        if (rawPublicKey == null || rawPublicKey.length != RAW_PUBLIC_KEY_LEN) return false;
        if (signature == null || signature.length != RAW_SIGNATURE_LEN) return false;
        if (message == null) return false;

        try {
            PublicKey publicKey = decodeRawPublicKey(rawPublicKey);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static PublicKey decodeRawPublicKey(byte[] rawPublicKey) throws GeneralSecurityException {
        byte[] x509 = new byte[X509_PREFIX.length + rawPublicKey.length];
        System.arraycopy(X509_PREFIX, 0, x509, 0, X509_PREFIX.length);
        System.arraycopy(rawPublicKey, 0, x509, X509_PREFIX.length, rawPublicKey.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
    }
}
