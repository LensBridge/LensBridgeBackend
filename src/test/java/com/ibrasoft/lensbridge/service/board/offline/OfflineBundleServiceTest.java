package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.Location;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.R2StorageService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import com.ibrasoft.lensbridge.service.board.BoardPayloadAssembler;
import com.ibrasoft.lensbridge.service.board.producer.FrameProducer;
import com.ibrasoft.lensbridge.service.board.producer.PosterFrameProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs the real assembler, the real {@link PosterFrameProducer} and the real signer (with a
 * fixed test key); only storage, the repositories and the clock are faked.
 * <p>
 * The clock is 23:30 on Friday 2026-10-30 in Toronto — already the 31st in UTC — and a
 * three-day bundle ends on Sunday 2026-11-01, the day Toronto leaves DST (25 hours long).
 */
class OfflineBundleServiceTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final Instant NOW = Instant.parse("2026-10-31T03:30:00Z");
    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-0000-4000-8000-000000000001");
    private static final String PUBLIC_URL = "https://media.example.com";

    private static final byte[] JPEG = "jpeg-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG = "png-bytes".getBytes(StandardCharsets.UTF_8);

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final PosterService posterService = mock(PosterService.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /** Seed 0x01..0x20: the cross-language test vector, see ContentSigningServiceTest. */
    private static final byte[] SEED = ContentSigningServiceTest.VECTOR_SEED;
    private final ContentSigningService signer =
            new ContentSigningService(Base64.getEncoder().encodeToString(SEED), "");

    private final List<Poster> posters = new ArrayList<>();
    private final List<BoardContext> seenContexts = new ArrayList<>();
    private Device device;

    @BeforeEach
    void setUp() throws IOException {
        device = Device.builder()
                .id(DEVICE_ID)
                .displayName("Lobby board")
                .audience(Audience.BROTHERS)
                .build();
        device.setConfig(DeviceConfig.builder()
                .location(Location.builder().timezone("America/Toronto").build())
                .build());
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));

        // Same predicate as PosterRepository.findPostersForAudienceOverlapping.
        when(posterService.getPostersForAudienceOverlapping(any(), any(), any())).thenAnswer(inv -> {
            Instant from = inv.getArgument(1);
            Instant to = inv.getArgument(2);
            return posters.stream()
                    .filter(p -> p.getStartTime().isBefore(to) && p.getEndTime().isAfter(from))
                    .toList();
        });

        when(r2.objectKeyFromPublicUrl(anyString())).thenAnswer(inv ->
                ((String) inv.getArgument(0)).substring(PUBLIC_URL.length() + 1));
        when(r2.getObject(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return new R2StorageService.R2Object(key, key.endsWith(".png") ? PNG : JPEG,
                    key.endsWith(".png") ? "image/png" : "image/jpeg");
        });
    }

    private OfflineBundleService service() {
        FrameProducer recorder = ctx -> {
            seenContexts.add(ctx);
            return List.of();
        };
        BoardPayloadAssembler assembler = new BoardPayloadAssembler(
                List.of(recorder, new PosterFrameProducer(posterService)), ZoneId.of("UTC"));
        return new OfflineBundleService(assembler, deviceRepository, r2, objectMapper, signer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Instant toronto(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(TORONTO).toInstant();
    }

    private Poster poster(String title, String objectKey, String start, String end) {
        Poster p = Poster.builder()
                .id(UUID.randomUUID())
                .title(title)
                .image(PUBLIC_URL + "/" + objectKey)
                .duration(10)
                .startTime(toronto(start))
                .endTime(toronto(end))
                .audience(Audience.BOTH)
                .build();
        posters.add(p);
        return p;
    }

    private static Map<String, byte[]> unzip(OfflineBundle bundle) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bundle.writeTo(out);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private static Map<String, Integer> zipMethods(OfflineBundle bundle) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bundle.writeTo(out);
        Map<String, Integer> methods = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                methods.put(entry.getName(), entry.getMethod());
            }
        }
        return methods;
    }

    private static JsonNode fileEntry(JsonNode manifest, String path) {
        for (JsonNode file : manifest.get("files")) {
            if (file.get("path").asText().equals(path)) return file;
        }
        throw new AssertionError("mbu.json files does not list " + path);
    }

    private JsonNode json(Map<String, byte[]> zip, String name) throws IOException {
        assertThat(zip).containsKey(name);
        return objectMapper.readTree(zip.get(name));
    }

    private static List<String> posterUrls(JsonNode payload) {
        List<String> urls = new ArrayList<>();
        payload.get("frames").forEach(f -> {
            if ("poster".equalsIgnoreCase(f.get("frameType").asText())) {
                urls.add(f.get("frameConfig").get("posterUrl").asText());
            }
        });
        return urls;
    }

    private static List<String> posterTitles(JsonNode payload) {
        List<String> titles = new ArrayList<>();
        payload.get("frames").forEach(f -> {
            JsonNode title = f.get("frameConfig") == null ? null : f.get("frameConfig").get("title");
            if (f.get("frameId").asText().startsWith("poster:")) titles.add(title.asText());
        });
        return titles;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    // ==================== Layout and manifest ====================

    @Test
    void zipHoldsTheManifestSignatureOnePayloadPerDayAndTheMediaAndNothingElse() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 3));

        assertThat(zip.keySet()).containsExactlyInAnyOrder(
                "mbu.json",
                "mbu.sig",
                "payloads/2026-10-30.json",
                "payloads/2026-10-31.json",
                "payloads/2026-11-01.json",
                "media/" + sha256(JPEG) + ".jpg");
    }

    @Test
    void manifestCarriesTheContractFields() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        JsonNode manifest = json(unzip(service().build(DEVICE_ID, 3)), "mbu.json");

        assertThat(manifest.fieldNames()).toIterable().containsExactly(
                "format", "formatVersion", "type", "createdAt", "sequence", "deviceId", "version", "files", "content");
        assertThat(manifest.get("format").asText()).isEqualTo("mbu");
        assertThat(manifest.get("formatVersion").isInt()).isTrue();
        assertThat(manifest.get("formatVersion").asInt()).isEqualTo(2);
        assertThat(manifest.get("type").asText()).isEqualTo("content");
        assertThat(manifest.get("createdAt").asText()).isEqualTo("2026-10-31T03:30:00Z");
        assertThat(manifest.get("sequence").isIntegralNumber()).isTrue();
        assertThat(manifest.get("sequence").asLong()).isEqualTo(NOW.toEpochMilli());
        assertThat(manifest.get("deviceId").asText()).isEqualTo(DEVICE_ID.toString());
        assertThat(manifest.get("version").isNull()).isTrue();

        JsonNode content = manifest.get("content");
        assertThat(content.get("timezone").asText()).isEqualTo("America/Toronto");
        // Today in Toronto, not in UTC (where it is already the 31st).
        assertThat(content.get("firstDay").asText()).isEqualTo("2026-10-30");
        assertThat(content.get("lastDay").asText()).isEqualTo("2026-11-01");

        String sha = sha256(JPEG);
        assertThat(content.get("media")).hasSize(1);
        JsonNode media = content.get("media").get(0);
        assertThat(media.get("path").asText()).isEqualTo("media/" + sha + ".jpg");
        assertThat(media.get("contentType").asText()).isEqualTo("image/jpeg");

        JsonNode mediaFile = fileEntry(manifest, "media/" + sha + ".jpg");
        assertThat(mediaFile.get("sha256").asText()).isEqualTo(sha);
        assertThat(mediaFile.get("bytes").asLong()).isEqualTo(JPEG.length);
    }

    /** Every zip entry but mbu.json and mbu.sig is listed with its true hash and size, and vice versa. */
    @Test
    void filesListsEveryPayloadAndMediaFileWithItsHashAndSize() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        poster("Iftar", "poster-b.png", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 3));
        JsonNode files = json(zip, "mbu.json").get("files");

        List<String> listed = new ArrayList<>();
        for (JsonNode file : files) {
            String path = file.get("path").asText();
            listed.add(path);
            assertThat(file.get("sha256").asText()).isEqualTo(sha256(zip.get(path)));
            assertThat(file.get("bytes").asLong()).isEqualTo(zip.get(path).length);
        }
        assertThat(listed).containsExactlyInAnyOrderElementsOf(zip.keySet().stream()
                .filter(n -> !n.equals("mbu.json") && !n.equals("mbu.sig")).toList());
    }

    @Test
    void signatureVerifiesOverThePrefixAndTheStoredManifestBytes() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 3));
        JsonNode sig = json(zip, "mbu.sig");

        assertThat(sig.fieldNames()).toIterable().containsExactly("signatures");
        assertThat(sig.get("signatures")).hasSize(1);
        JsonNode entry = sig.get("signatures").get(0);
        assertThat(entry.get("keyId").asText()).isEqualTo(ContentSigningServiceTest.VECTOR_KEY_ID);

        byte[] prefix = "musallahboard-mbu-v2\n".getBytes(StandardCharsets.US_ASCII);
        byte[] manifest = zip.get("mbu.json");
        byte[] message = new byte[prefix.length + manifest.length];
        System.arraycopy(prefix, 0, message, 0, prefix.length);
        System.arraycopy(manifest, 0, message, prefix.length, manifest.length);
        byte[] signature = Base64.getDecoder().decode(entry.get("sig").asText());
        assertThat(signature).hasSize(64);

        byte[] publicKey = Base64.getDecoder().decode(signer.publicKeys().get(0).getPublicKey());
        assertThat(ContentSigningServiceTest.jdkVerify(publicKey, message, signature)).isTrue();

        // One byte changed anywhere in the manifest and it no longer verifies.
        message[message.length - 2] ^= 1;
        assertThat(ContentSigningServiceTest.jdkVerify(publicKey, message, signature)).isFalse();
    }

    @Test
    void mediaIsStoredAndJsonIsDeflated() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, Integer> methods = zipMethods(service().build(DEVICE_ID, 2));

        assertThat(methods.get("media/" + sha256(JPEG) + ".jpg")).isEqualTo(ZipEntry.STORED);
        assertThat(methods.get("mbu.json")).isEqualTo(ZipEntry.DEFLATED);
        assertThat(methods.get("mbu.sig")).isEqualTo(ZipEntry.DEFLATED);
        assertThat(methods.get("payloads/2026-10-30.json")).isEqualTo(ZipEntry.DEFLATED);
    }

    @Test
    void filenameFollowsTheContentPackageConvention() {
        assertThat(service().build(DEVICE_ID, 14).filename())
                .isEqualTo("musallahboard-content-3f2a1b4c-2026-10-30.mbu");
    }

    @Test
    void defaultWindowOfFourteenDaysHasFourteenPayloads() throws Exception {
        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 14));

        assertThat(zip.keySet().stream().filter(n -> n.startsWith("payloads/"))).hasSize(14);
        assertThat(json(zip, "mbu.json").get("content").get("lastDay").asText()).isEqualTo("2026-11-12");
    }

    // ==================== Signing key ====================

    @Test
    void withoutASigningKeyTheBuildIsA503AndNothingIsAssembledOrFetched() {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        BoardPayloadAssembler assembler = new BoardPayloadAssembler(
                List.of(new PosterFrameProducer(posterService)), ZoneId.of("UTC"));
        OfflineBundleService unsigned = new OfflineBundleService(assembler, deviceRepository, r2, objectMapper,
                new ContentSigningService("", ""), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> unsigned.build(DEVICE_ID, 3))
                .isInstanceOfSatisfying(ApiResponseException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(((ErrorResponse) e.getBody()).getError()).contains("signing is not configured");
                });
        verifyNoInteractions(posterService);
        verifyNoInteractions(r2);
    }

    // ==================== haveMedia (online delta sync) ====================

    @Test
    void mediaTheBoardHoldsIsListedAndSignedButLeftOutOfTheZip() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        poster("Iftar", "poster-b.png", "2026-10-01T00:00", "2026-12-01T00:00");
        String jpeg = "media/" + sha256(JPEG) + ".jpg";
        String png = "media/" + sha256(PNG) + ".png";

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 2, Set.of(sha256(JPEG))));

        assertThat(zip).doesNotContainKey(jpeg).containsKey(png);
        JsonNode manifest = json(zip, "mbu.json");
        assertThat(fileEntry(manifest, jpeg).get("sha256").asText()).isEqualTo(sha256(JPEG));
        assertThat(fileEntry(manifest, jpeg).get("bytes").asLong()).isEqualTo(JPEG.length);
        assertThat(manifest.get("content").get("media")).hasSize(2);
        assertThat(posterUrls(json(zip, "payloads/2026-10-30.json")))
                .containsExactlyInAnyOrder("/" + jpeg, "/" + png);
    }

    /**
     * The first build learns each image's hash by downloading it. A later sync from a board
     * that holds the image must not download it again.
     */
    @Test
    void aSyncWhereTheBoardHoldsEveryImageDownloadsNothingOnceHashesAreKnown() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        OfflineBundleService service = service();

        Map<String, byte[]> first = unzip(service.build(DEVICE_ID, 7));
        verify(r2, times(1)).getObject("poster-a.jpg");
        assertThat(first).containsKey("media/" + sha256(JPEG) + ".jpg");

        Map<String, byte[]> second = unzip(service.build(DEVICE_ID, 7, Set.of(sha256(JPEG))));

        verify(r2, times(1)).getObject("poster-a.jpg"); // still just the first download
        assertThat(second.keySet().stream().filter(n -> n.startsWith("media/"))).isEmpty();
        assertThat(fileEntry(json(second, "mbu.json"), "media/" + sha256(JPEG) + ".jpg")).isNotNull();
    }

    @Test
    void anImageTheBoardLacksIsDownloadedEvenWhenItsHashIsCached() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        OfflineBundleService service = service();
        service.build(DEVICE_ID, 1);

        Map<String, byte[]> zip = unzip(service.build(DEVICE_ID, 1, Set.of(sha256(PNG))));

        verify(r2, times(2)).getObject("poster-a.jpg");
        assertThat(zip.get("media/" + sha256(JPEG) + ".jpg")).isEqualTo(JPEG);
    }

    /** Hash not cached yet: the image is downloaded to learn it, but still left out of the zip. */
    @Test
    void anUncachedImageTheBoardHoldsIsHashedButNotShipped() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 1, Set.of(sha256(JPEG))));

        verify(r2, times(1)).getObject("poster-a.jpg");
        assertThat(zip.keySet().stream().filter(n -> n.startsWith("media/"))).isEmpty();
    }

    // ==================== Per-day assembly ====================

    @Test
    void eachDayIsAssembledAtTheStartOfThatDayInTheDeviceZone() {
        service().build(DEVICE_ID, 3);

        assertThat(seenContexts).extracting(BoardContext::getNow).containsExactly(
                ZonedDateTime.of(2026, 10, 30, 0, 0, 0, 0, TORONTO),
                ZonedDateTime.of(2026, 10, 31, 0, 0, 0, 0, TORONTO),
                ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, TORONTO));
    }

    @Test
    void weatherIsAlwaysNull() throws Exception {
        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 2));

        JsonNode payload = json(zip, "payloads/2026-10-30.json");
        assertThat(payload.has("weather")).isTrue();
        assertThat(payload.get("weather").isNull()).isTrue();
    }

    @Test
    void payloadIsSerializedInThePerDayFormat() throws Exception {
        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 1));

        JsonNode payload = json(zip, "payloads/2026-10-30.json");
        assertThat(payload.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("deviceConfig", "frames", "weather");
        assertThat(payload.get("deviceConfig").get("location").get("timezone").asText())
                .isEqualTo("America/Toronto");
    }

    // ==================== Poster day-overlap ====================

    @Test
    void posterIsIncludedOnEveryDayItOverlapsAndNoOther() throws Exception {
        // A two-hour afternoon slot: not active at midnight, but active during the 31st.
        poster("Afternoon", "a.jpg", "2026-10-31T14:00", "2026-10-31T16:00");
        // Ends exactly at the 1st's midnight — end is exclusive, so not on the 1st.
        poster("Ends at midnight", "b.jpg", "2026-10-29T09:00", "2026-11-01T00:00");
        // Starts exactly at the midnight after the window.
        poster("Starts after", "c.jpg", "2026-11-02T00:00", "2026-11-09T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 3));

        assertThat(posterTitles(json(zip, "payloads/2026-10-30.json")))
                .containsExactlyInAnyOrder("Ends at midnight");
        assertThat(posterTitles(json(zip, "payloads/2026-10-31.json")))
                .containsExactlyInAnyOrder("Afternoon", "Ends at midnight");
        assertThat(posterTitles(json(zip, "payloads/2026-11-01.json"))).isEmpty();
        verify(r2, never()).getObject("c.jpg");
    }

    /**
     * 2026-11-01 is 25 hours long in Toronto. A day end computed as "start + 24h" would stop
     * at 23:00 local and miss a poster that starts at 23:30.
     */
    @Test
    void dayWindowFollowsTheZoneAcrossTheDstChange() throws Exception {
        poster("Late", "late.jpg", "2026-11-01T23:30", "2026-11-02T08:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 3));

        assertThat(posterTitles(json(zip, "payloads/2026-11-01.json"))).containsExactly("Late");
        assertThat(posterTitles(json(zip, "payloads/2026-10-31.json"))).isEmpty();

        BoardContext sunday = seenContexts.get(2);
        assertThat(sunday.currentDayStart()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
        assertThat(sunday.nextDayStart()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
    }

    // ==================== Media ====================

    @Test
    void posterUrlsAreRewrittenToBundlePaths() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        poster("Iftar", "poster-b.png", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 2));

        for (String day : List.of("2026-10-30", "2026-10-31")) {
            assertThat(posterUrls(json(zip, "payloads/" + day + ".json"))).containsExactlyInAnyOrder(
                    "/media/" + sha256(JPEG) + ".jpg",
                    "/media/" + sha256(PNG) + ".png");
        }
        assertThat(zip.get("media/" + sha256(JPEG) + ".jpg")).isEqualTo(JPEG);
        assertThat(zip.get("media/" + sha256(PNG) + ".png")).isEqualTo(PNG);
    }

    @Test
    void eachImageIsFetchedOnceAndIdenticalBytesAreStoredOnce() throws Exception {
        // Same bytes under two different keys (the fake returns JPEG for every .jpg key).
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        poster("Halaqa again", "poster-copy.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 14));

        verify(r2, times(1)).getObject("poster-a.jpg");
        verify(r2, times(1)).getObject("poster-copy.jpg");
        assertThat(zip.keySet().stream().filter(n -> n.startsWith("media/"))).hasSize(1);
        assertThat(json(zip, "mbu.json").get("content").get("media")).hasSize(1);
        assertThat(posterUrls(json(zip, "payloads/2026-11-05.json")))
                .containsOnly("/media/" + sha256(JPEG) + ".jpg");
    }

    @Test
    void extensionAndContentTypeFallBackToTheObjectKey() throws Exception {
        poster("Untyped", "poster-u.PNG", "2026-10-01T00:00", "2026-12-01T00:00");
        when(r2.getObject("poster-u.PNG")).thenReturn(new R2StorageService.R2Object("poster-u.PNG", PNG, null));

        JsonNode manifest = json(unzip(service().build(DEVICE_ID, 1)), "mbu.json");

        JsonNode media = manifest.get("content").get("media").get(0);
        assertThat(media.get("path").asText()).isEqualTo("media/" + sha256(PNG) + ".png");
        assertThat(media.get("contentType").asText()).isEqualTo("image/png");
    }

    @Test
    void aGenericStoredContentTypeDefersToTheObjectKey() throws Exception {
        poster("Octet", "poster-o.jpeg", "2026-10-01T00:00", "2026-12-01T00:00");
        when(r2.getObject("poster-o.jpeg")).thenReturn(
                new R2StorageService.R2Object("poster-o.jpeg", JPEG, "application/octet-stream"));

        JsonNode media = json(unzip(service().build(DEVICE_ID, 1)), "mbu.json").get("content").get("media").get(0);

        assertThat(media.get("path").asText()).isEqualTo("media/" + sha256(JPEG) + ".jpeg");
        assertThat(media.get("contentType").asText()).isEqualTo("image/jpeg");
    }

    /** The agent accepts only a fixed list of media types; anything else must not reject the package. */
    @Test
    void aStoredTypeTheAgentDoesNotAcceptBecomesOctetStream() throws Exception {
        byte[] heic = "heic-bytes".getBytes(StandardCharsets.UTF_8);
        poster("Phone photo", "poster-h.heic", "2026-10-01T00:00", "2026-12-01T00:00");
        when(r2.getObject("poster-h.heic")).thenReturn(
                new R2StorageService.R2Object("poster-h.heic", heic, "image/heic"));

        JsonNode media = json(unzip(service().build(DEVICE_ID, 1)), "mbu.json").get("content").get("media").get(0);

        assertThat(media.get("path").asText()).isEqualTo("media/" + sha256(heic) + ".heic");
        assertThat(media.get("contentType").asText()).isEqualTo("application/octet-stream");
    }

    @Test
    void anUnfetchableImageFailsTheWholeExportWith502NamingThePoster() throws Exception {
        poster("Fine", "fine.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        Poster broken = poster("Eid Dinner", "gone.jpg", "2026-10-01T00:00", "2026-12-01T00:00");
        when(r2.getObject("gone.jpg")).thenThrow(new IOException("NoSuchKey"));

        assertThatThrownBy(() -> service().build(DEVICE_ID, 3))
                .isInstanceOfSatisfying(ApiResponseException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    String message = ((ErrorResponse) e.getBody()).getError();
                    assertThat(message).contains("Eid Dinner").contains(broken.getId().toString());
                });
    }

    // ==================== Validation ====================

    @Test
    void daysOutsideOneToThirtyOneIsA400() {
        for (int days : new int[]{0, -1, 32}) {
            assertThatThrownBy(() -> service().build(DEVICE_ID, days))
                    .isInstanceOfSatisfying(ApiResponseException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThat(service().build(DEVICE_ID, 31).entries())
                .filteredOn(e -> e.name().startsWith("payloads/")).hasSize(31);
    }

    @Test
    void unknownDeviceIsA404() {
        UUID missing = UUID.randomUUID();
        when(deviceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().build(missing, 14))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void revokedDeviceIsA409() {
        device.setRevokedAt(Instant.parse("2026-10-01T00:00:00Z"));

        assertThatThrownBy(() -> service().build(DEVICE_ID, 14))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(posterService);
    }

    @Test
    void deviceWithoutATimezoneUsesTheDefaultZone() throws Exception {
        device.setConfig(null);

        JsonNode content = json(unzip(service().build(DEVICE_ID, 1)), "mbu.json").get("content");

        // The assembler's default zone here is UTC, where NOW is already the 31st.
        assertThat(content.get("timezone").asText()).isEqualTo("UTC");
        assertThat(content.get("firstDay").asText()).isEqualTo("2026-10-31");
    }
}
