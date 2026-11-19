package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checks the board's agent applies to every package before it looks at its type
 * ({@code agent/internal/mbu}: {@code reader.go} open/classify/VerifySignature and
 * {@code manifest.go} ParseManifest), written out independently of the code under test, so a
 * content package and a provision package can be held to the same rules.
 */
final class AgentPackageRules {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SEGMENT = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9._-]*$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern UUID_LIKE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern RFC3339_SECONDS_UTC = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");

    /** A package the agent accepted: its zip entries and its parsed manifest. */
    record Verified(Map<String, byte[]> entries, JsonNode manifest, String signedBy) {}

    private AgentPackageRules() {}

    static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                assertThat(entries).as("duplicate entry %s", entry.getName()).doesNotContainKey(entry.getName());
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    static byte[] bytesOf(OfflineBundle bundle) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bundle.writeTo(out);
        return out.toByteArray();
    }

    /**
     * @param contentKey raw 32-byte public content key the board has pinned
     */
    static Verified verify(byte[] zip, byte[] contentKey) throws Exception {
        Map<String, byte[]> entries = unzip(zip);
        for (String name : entries.keySet()) {
            assertThat(name.length()).isBetween(1, 200);
            String[] segments = name.split("/", -1);
            assertThat(segments.length).isLessThanOrEqualTo(8);
            for (String s : segments) assertThat(s).matches(SEGMENT);
        }

        byte[] rawManifest = entries.get("mbu.json");
        byte[] rawSig = entries.get("mbu.sig");
        assertThat(rawManifest).as("mbu.json").isNotNull();
        assertThat(rawSig).as("mbu.sig (unsigned packages are refused)").isNotNull();

        // Signature first, over the exact stored manifest bytes.
        String keyId = ContentSigningService.keyId(contentKey);
        byte[] message = ("musallahboard-mbu-v2\n").getBytes(StandardCharsets.US_ASCII);
        byte[] signed = new byte[message.length + rawManifest.length];
        System.arraycopy(message, 0, signed, 0, message.length);
        System.arraycopy(rawManifest, 0, signed, message.length, rawManifest.length);
        String signedBy = null;
        for (JsonNode s : MAPPER.readTree(rawSig).get("signatures")) {
            if (!keyId.equals(s.get("keyId").asText())) continue; // unknown keys are ignored
            if (ContentSigningServiceTest.jdkVerify(contentKey, signed, Base64.getDecoder().decode(s.get("sig").asText()))) {
                signedBy = keyId;
            }
        }
        assertThat(signedBy).as("a signature by the pinned content key verifies").isNotNull();

        JsonNode m = MAPPER.readTree(rawManifest);
        assertThat(m.get("format").asText()).isEqualTo("mbu");
        assertThat(m.get("formatVersion").isInt()).isTrue();
        assertThat(m.get("formatVersion").asInt()).isEqualTo(2);
        assertThat(m.get("createdAt").asText()).matches(RFC3339_SECONDS_UTC);
        Instant.parse(m.get("createdAt").asText());
        assertThat(m.get("sequence").isIntegralNumber()).isTrue();
        assertThat(m.get("sequence").asLong()).isPositive();
        assertThat(m.get("deviceId").asText()).matches(UUID_LIKE);

        Map<String, JsonNode> listed = new LinkedHashMap<>();
        for (JsonNode f : m.get("files")) {
            String path = f.get("path").asText();
            assertThat(path).isNotIn("mbu.json", "mbu.sig");
            assertThat(f.get("sha256").asText()).matches(SHA256);
            assertThat(f.get("bytes").asLong()).isNotNegative();
            assertThat(listed.put(path, f)).as("listed twice: %s", path).isNull();
        }
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().equals("mbu.json") || e.getKey().equals("mbu.sig")) continue;
            JsonNode f = listed.get(e.getKey());
            assertThat(f).as("%s is in the zip but not in the manifest", e.getKey()).isNotNull();
            assertThat(f.get("bytes").asLong()).isEqualTo(e.getValue().length);
            assertThat(f.get("sha256").asText()).isEqualTo(sha256(e.getValue()));
        }
        for (String path : listed.keySet()) {
            assertThat(entries).as("%s is listed but missing", path).containsKey(path);
        }
        return new Verified(entries, m, signedBy);
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
