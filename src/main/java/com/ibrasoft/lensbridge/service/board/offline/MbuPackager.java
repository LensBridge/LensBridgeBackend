package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import org.springframework.http.HttpStatus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * The one place a {@code .mbu} is signed and laid out, for every package type this backend
 * builds (content and provision). See MusallahBoard {@code agent/docs/architecture.md},
 * sections 4 and 4.2.
 * <p>
 * The manifest is serialized exactly once. Those bytes are signed and those same bytes are
 * stored as {@code mbu.json}; the signature covers
 * {@value ContentSigningService#MBU_SIGNATURE_PREFIX} followed by them, and nothing else.
 */
final class MbuPackager {

    static final String MANIFEST_ENTRY = "mbu.json";
    static final String SIGNATURE_ENTRY = "mbu.sig";

    private final ObjectMapper objectMapper;
    private final ContentSigningService signingService;

    MbuPackager(ObjectMapper objectMapper, ContentSigningService signingService) {
        this.objectMapper = objectMapper;
        this.signingService = signingService;
    }

    /**
     * @param files the package's files in zip order, after {@code mbu.json} and {@code mbu.sig};
     *              the manifest must already list each of them (and may list more, such as
     *              media the board already holds)
     */
    OfflineBundle pack(String filename, MbuManifest manifest, List<OfflineBundle.Entry> files) {
        byte[] manifestBytes = toJson(manifest);
        byte[] signature = signingService.signManifest(manifestBytes);
        byte[] signatureFile = toJson(new SignatureFile(List.of(
                new SignatureEntry(signingService.currentKeyId(), Base64.getEncoder().encodeToString(signature)))));

        List<OfflineBundle.Entry> entries = new ArrayList<>(files.size() + 2);
        entries.add(new OfflineBundle.Entry(MANIFEST_ENTRY, manifestBytes, false));
        entries.add(new OfflineBundle.Entry(SIGNATURE_ENTRY, signatureFile, false));
        entries.addAll(files);
        return new OfflineBundle(filename, entries);
    }

    byte[] toJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new ApiResponseException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorResponse.of("Could not serialize package: " + e.getOriginalMessage()));
        }
    }

    static MbuManifest.FileEntry fileEntry(String path, byte[] bytes) {
        return new MbuManifest.FileEntry(path, sha256Hex(bytes), bytes.length);
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    /** {@code mbu.sig}: {@code {"signatures":[{"keyId","sig"}]}}. */
    private record SignatureFile(List<SignatureEntry> signatures) {}

    @JsonPropertyOrder({"keyId", "sig"})
    private record SignatureEntry(String keyId, String sig) {}
}
