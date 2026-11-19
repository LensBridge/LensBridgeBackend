package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.Location;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.R2StorageService;
import com.ibrasoft.lensbridge.service.board.BoardPayloadAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The setup file (contract C1) with the real signer and packager. The device is the one of
 * the claim test vector (see DeviceClaimServiceTest): id ...0001, claim secret 32 x 0x01.
 */
class SetupFileServiceTest {

    static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00.250Z");
    private static final String BACKEND = "https://api.example.org";

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final ContentSigningService signer = new ContentSigningService(
            Base64.getEncoder().encodeToString(ContentSigningServiceTest.VECTOR_SEED), "");
    private final byte[] contentKey = Base64.getDecoder().decode(signer.publicKeys().get(0).getPublicKey());
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private Device device;

    /** Fills every "random" byte with 0x01, so the claim secret is the test vector's. */
    private static final class OnesRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, (byte) 0x01);
        }
    }

    @BeforeEach
    void setUp() {
        device = Device.builder().id(DEVICE_ID).displayName("North Musallah").audience(Audience.BOTH).build();
        device.setConfig(DeviceConfig.builder().location(Location.builder().timezone("America/Toronto").build()).build());
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SetupFileService service(Instant now, String backendUrl, SecureRandom random) {
        return new SetupFileService(deviceRepository, objectMapper, signer, backendUrl,
                Clock.fixed(now, ZoneOffset.UTC), random);
    }

    private SetupFileService service() {
        return service(NOW, BACKEND, new OnesRandom());
    }

    private static JsonNode provisionJson(AgentPackageRules.Verified pkg) throws Exception {
        return new ObjectMapper().readTree(pkg.entries().get(SetupFileService.PROVISION_ENTRY));
    }

    @Test
    void anOfflineSetupFilePassesTheAgentsPackageChecks() throws Exception {
        OfflineBundle file = service().build(DEVICE_ID, false);
        AgentPackageRules.Verified pkg = AgentPackageRules.verify(AgentPackageRules.bytesOf(file), contentKey);

        assertThat(file.filename()).isEqualTo("musallahboard-setup-00000000.mbu");
        JsonNode m = pkg.manifest();
        assertThat(m.get("type").asText()).isEqualTo("provision");
        assertThat(m.get("deviceId").asText()).isEqualTo(DEVICE_ID.toString());
        assertThat(m.get("createdAt").asText()).isEqualTo("2026-11-19T12:00:00Z");
        assertThat(m.get("sequence").asLong()).isEqualTo(NOW.toEpochMilli());
        assertThat(m.get("version").isNull()).isTrue();
        assertThat(m.has("content")).isFalse();
        assertThat(m.get("files")).hasSize(1);
        assertThat(m.get("files").get(0).get("path").asText()).isEqualTo("provision.json");
        assertThat(pkg.entries().keySet()).containsExactly("mbu.json", "mbu.sig", "provision.json");

        JsonNode p = provisionJson(pkg);
        assertThat(p.fieldNames()).toIterable()
                .containsExactly("format", "deviceId", "displayName", "sequence", "issuedAt");
        assertThat(p.get("format").asInt()).isEqualTo(1);
        assertThat(p.get("deviceId").asText()).isEqualTo(DEVICE_ID.toString());
        assertThat(p.get("displayName").asText()).isEqualTo("North Musallah");
        assertThat(p.get("sequence").asLong()).isEqualTo(m.get("sequence").asLong());
        assertThat(p.get("issuedAt").asText()).isEqualTo(m.get("createdAt").asText());
        // An offline file leaves any stored claim secret alone.
        assertThat(device.getClaimSecret()).isNull();
    }

    @Test
    void anOnlineSetupFileCarriesTheBackendAndANewClaimSecret() throws Exception {
        byte[] old = new byte[32];
        device.setClaimSecret(old);

        AgentPackageRules.Verified pkg = AgentPackageRules.verify(
                AgentPackageRules.bytesOf(service().build(DEVICE_ID, true)), contentKey);

        JsonNode p = provisionJson(pkg);
        assertThat(p.fieldNames()).toIterable().containsExactly(
                "format", "deviceId", "displayName", "sequence", "issuedAt", "backendUrl", "claimSecret");
        assertThat(p.get("backendUrl").asText()).isEqualTo(BACKEND);
        byte[] ones = new byte[32];
        Arrays.fill(ones, (byte) 0x01);
        // base64url without padding: 43 characters for 32 bytes.
        assertThat(p.get("claimSecret").asText())
                .isEqualTo("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE")
                .hasSize(43);
        assertThat(Base64.getUrlDecoder().decode(p.get("claimSecret").asText())).isEqualTo(ones);
        assertThat(device.getClaimSecret()).as("stored for the claim, replacing the old one").isEqualTo(ones);
    }

    @Test
    void everyOnlineFileRotatesTheSecret() throws Exception {
        SetupFileService real = service(NOW, BACKEND, new SecureRandom());
        String first = provisionJson(AgentPackageRules.verify(AgentPackageRules.bytesOf(real.build(DEVICE_ID, true)), contentKey))
                .get("claimSecret").asText();
        String second = provisionJson(AgentPackageRules.verify(AgentPackageRules.bytesOf(real.build(DEVICE_ID, true)), contentKey))
                .get("claimSecret").asText();
        assertThat(second).isNotEqualTo(first);
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(device.getClaimSecret())).isEqualTo(second);
    }

    /** A board only takes a strictly newer file, so a second download must be newer even within one millisecond. */
    @Test
    void theSequenceAlwaysGoesUp() throws Exception {
        long first = AgentPackageRules.verify(AgentPackageRules.bytesOf(service().build(DEVICE_ID, false)), contentKey)
                .manifest().get("sequence").asLong();
        long sameMilli = AgentPackageRules.verify(AgentPackageRules.bytesOf(service().build(DEVICE_ID, false)), contentKey)
                .manifest().get("sequence").asLong();
        long clockWentBack = AgentPackageRules.verify(AgentPackageRules.bytesOf(
                        service(NOW.minusSeconds(3600), BACKEND, new OnesRandom()).build(DEVICE_ID, false)), contentKey)
                .manifest().get("sequence").asLong();

        assertThat(sameMilli).isGreaterThan(first);
        assertThat(clockWentBack).isGreaterThan(sameMilli);
        assertThat(device.getProvisionSequence()).isEqualTo(clockWentBack);
    }

    /**
     * Contract C1: the provision manifest mirrors a content manifest's shape exactly (same
     * fields, same order, same signature format and key), minus the content block. Both are
     * held to the agent's common package rules.
     */
    @Test
    void theProvisionManifestHasTheContentManifestsShape() throws Exception {
        BoardPayloadAssembler assembler = new BoardPayloadAssembler(List.of(), ZoneId.of("UTC"));
        OfflineBundleService content = new OfflineBundleService(assembler, deviceRepository, mock(R2StorageService.class),
                objectMapper, signer, Clock.fixed(NOW, ZoneOffset.UTC));
        JsonNode contentManifest = AgentPackageRules.verify(
                AgentPackageRules.bytesOf(content.build(DEVICE_ID, 2)), contentKey).manifest();
        JsonNode provisionManifest = AgentPackageRules.verify(
                AgentPackageRules.bytesOf(service().build(DEVICE_ID, false)), contentKey).manifest();

        assertThat(contentManifest.fieldNames()).toIterable().containsExactly(
                "format", "formatVersion", "type", "createdAt", "sequence", "deviceId", "version", "files", "content");
        assertThat(provisionManifest.fieldNames()).toIterable().containsExactly(
                "format", "formatVersion", "type", "createdAt", "sequence", "deviceId", "version", "files");
        for (String field : List.of("format", "formatVersion", "createdAt", "deviceId", "version")) {
            assertThat(provisionManifest.get(field)).as(field).isEqualTo(contentManifest.get(field));
        }
        assertThat(provisionManifest.get("files").get(0).fieldNames()).toIterable()
                .containsExactly("path", "sha256", "bytes");
    }

    @Test
    void tamperingWithProvisionJsonIsCaught() throws Exception {
        AgentPackageRules.Verified pkg = AgentPackageRules.verify(
                AgentPackageRules.bytesOf(service().build(DEVICE_ID, false)), contentKey);
        String listed = pkg.manifest().get("files").get(0).get("sha256").asText();
        byte[] edited = new String(pkg.entries().get("provision.json")).replace("North", "South").getBytes();
        assertThat(AgentPackageRules.sha256(edited)).isNotEqualTo(listed);
    }

    @Test
    void unknownRevokedAndUnsignedAreRefusedBeforeAnythingIsStored() {
        UUID missing = UUID.randomUUID();
        when(deviceRepository.findById(missing)).thenReturn(Optional.empty());
        assertStatus(() -> service().build(missing, false), HttpStatus.NOT_FOUND);

        device.setRevokedAt(NOW);
        assertStatus(() -> service().build(DEVICE_ID, true), HttpStatus.CONFLICT);
        device.setRevokedAt(null);

        SetupFileService unsigned = new SetupFileService(deviceRepository, objectMapper,
                new ContentSigningService("", ""), BACKEND, Clock.fixed(NOW, ZoneOffset.UTC), new OnesRandom());
        assertStatus(() -> unsigned.build(DEVICE_ID, true), HttpStatus.SERVICE_UNAVAILABLE);

        assertStatus(() -> service(NOW, null, new OnesRandom()).build(DEVICE_ID, true), HttpStatus.SERVICE_UNAVAILABLE);
        verify(deviceRepository, never()).save(any());
        assertThat(device.getClaimSecret()).isNull();
    }

    @Test
    void anOfflineFileNeedsNoBackendUrl() throws Exception {
        AgentPackageRules.verify(AgentPackageRules.bytesOf(service(NOW, null, new OnesRandom()).build(DEVICE_ID, false)),
                contentKey);
    }

    @Test
    void theBackendUrlIsConfiguredOrTheAgentSocketsOrigin() {
        assertThat(SetupFileService.resolveBackendUrl("https://api.example.org/", "wss://other/api/agent/ws"))
                .isEqualTo("https://api.example.org");
        assertThat(SetupFileService.resolveBackendUrl("", "wss://api.example.org/api/agent/ws"))
                .isEqualTo("https://api.example.org");
        assertThat(SetupFileService.resolveBackendUrl(null, "ws://localhost:8080/api/agent/ws"))
                .isEqualTo("http://localhost:8080");
        assertThat(SetupFileService.resolveBackendUrl("", "")).isNull();
        assertThat(SetupFileService.resolveBackendUrl("", "not a url")).isNull();
    }

    /**
     * Writes one real online setup file for the agent's tests to read: device ...0001, claim
     * secret 32 x 0x01, signed by the content key of seed 0x01..0x20 (public key
     * {@code ContentSigningServiceTest}'s vector). target/sample-provision.mbu.
     */
    @Test
    void writesASampleForTheAgent() throws Exception {
        byte[] zip = AgentPackageRules.bytesOf(service().build(DEVICE_ID, true));
        AgentPackageRules.verify(zip, contentKey);
        Path out = Path.of("target", "sample-provision.mbu");
        Files.createDirectories(out.getParent());
        Files.write(out, zip);
        System.out.println("sample provision package: " + out.toAbsolutePath()
                + " contentPublicKey=" + signer.publicKeys().get(0).getPublicKey()
                + " keyId=" + signer.currentKeyId());
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiResponseException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }
}
