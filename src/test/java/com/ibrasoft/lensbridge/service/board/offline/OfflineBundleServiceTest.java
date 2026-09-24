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
import com.ibrasoft.lensbridge.service.OpenWeatherService;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Runs the real assembler and the real {@link PosterFrameProducer}; only storage, the
 * repositories and the clock are faked.
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
    private final OpenWeatherService weatherService = mock(OpenWeatherService.class);
    private final PosterService posterService = mock(PosterService.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

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
                deviceRepository, weatherService,
                List.of(recorder, new PosterFrameProducer(posterService)), ZoneId.of("UTC"));
        return new OfflineBundleService(assembler, deviceRepository, r2, objectMapper,
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
    void zipHoldsTheManifestOnePayloadPerDayAndTheMediaAndNothingElse() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 3));

        assertThat(zip.keySet()).containsExactlyInAnyOrder(
                "manifest.json",
                "payloads/2026-10-30.json",
                "payloads/2026-10-31.json",
                "payloads/2026-11-01.json",
                "media/" + sha256(JPEG) + ".jpg");
    }

    @Test
    void manifestCarriesTheContractFields() throws Exception {
        poster("Halaqa", "poster-a.jpg", "2026-10-01T00:00", "2026-12-01T00:00");

        JsonNode manifest = json(unzip(service().build(DEVICE_ID, 3)), "manifest.json");

        assertThat(manifest.get("formatVersion").isInt()).isTrue();
        assertThat(manifest.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(manifest.get("deviceId").asText()).isEqualTo(DEVICE_ID.toString());
        assertThat(manifest.get("timezone").asText()).isEqualTo("America/Toronto");
        assertThat(manifest.get("generatedAt").asText()).isEqualTo("2026-10-31T03:30:00Z");
        // Today in Toronto, not in UTC (where it is already the 31st).
        assertThat(manifest.get("firstDay").asText()).isEqualTo("2026-10-30");
        assertThat(manifest.get("lastDay").asText()).isEqualTo("2026-11-01");

        String sha = sha256(JPEG);
        assertThat(manifest.get("media")).hasSize(1);
        JsonNode media = manifest.get("media").get(0);
        assertThat(media.get("path").asText()).isEqualTo("media/" + sha + ".jpg");
        assertThat(media.get("sha256").asText()).isEqualTo(sha);
        assertThat(media.get("bytes").asLong()).isEqualTo(JPEG.length);
        assertThat(media.get("contentType").asText()).isEqualTo("image/jpeg");
    }

    @Test
    void filenameUsesTheFirstEightCharactersOfTheIdAndTheFirstDay() {
        assertThat(service().build(DEVICE_ID, 14).filename())
                .isEqualTo("musallahboard-3f2a1b4c-2026-10-30.zip");
    }

    @Test
    void defaultWindowOfFourteenDaysHasFourteenPayloads() throws Exception {
        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 14));

        assertThat(zip.keySet().stream().filter(n -> n.startsWith("payloads/"))).hasSize(14);
        assertThat(json(zip, "manifest.json").get("lastDay").asText()).isEqualTo("2026-11-12");
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
    void weatherIsNullAndOpenWeatherIsNeverCalled() throws Exception {
        Map<String, byte[]> zip = unzip(service().build(DEVICE_ID, 2));

        JsonNode payload = json(zip, "payloads/2026-10-30.json");
        assertThat(payload.has("weather")).isTrue();
        assertThat(payload.get("weather").isNull()).isTrue();
        verifyNoInteractions(weatherService);
    }

    @Test
    void payloadIsSerializedLikeTheLiveEndpoint() throws Exception {
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
        assertThat(json(zip, "manifest.json").get("media")).hasSize(1);
        assertThat(posterUrls(json(zip, "payloads/2026-11-05.json")))
                .containsOnly("/media/" + sha256(JPEG) + ".jpg");
    }

    @Test
    void extensionAndContentTypeFallBackToTheObjectKey() throws Exception {
        poster("Untyped", "poster-u.PNG", "2026-10-01T00:00", "2026-12-01T00:00");
        when(r2.getObject("poster-u.PNG")).thenReturn(new R2StorageService.R2Object("poster-u.PNG", PNG, null));

        JsonNode manifest = json(unzip(service().build(DEVICE_ID, 1)), "manifest.json");

        JsonNode media = manifest.get("media").get(0);
        assertThat(media.get("path").asText()).isEqualTo("media/" + sha256(PNG) + ".png");
        assertThat(media.get("contentType").asText()).isEqualTo("image/png");
    }

    @Test
    void aGenericStoredContentTypeDefersToTheObjectKey() throws Exception {
        poster("Octet", "poster-o.jpeg", "2026-10-01T00:00", "2026-12-01T00:00");
        when(r2.getObject("poster-o.jpeg")).thenReturn(
                new R2StorageService.R2Object("poster-o.jpeg", JPEG, "application/octet-stream"));

        JsonNode media = json(unzip(service().build(DEVICE_ID, 1)), "manifest.json").get("media").get(0);

        assertThat(media.get("path").asText()).isEqualTo("media/" + sha256(JPEG) + ".jpeg");
        assertThat(media.get("contentType").asText()).isEqualTo("image/jpeg");
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
    void theLivePosterQueryIsNeverUsedForABundle() {
        service().build(DEVICE_ID, 3);

        verify(posterService, never()).getActivePosterFramesForAudience(any());
    }

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

        JsonNode manifest = json(unzip(service().build(DEVICE_ID, 1)), "manifest.json");

        // The assembler's default zone here is UTC, where NOW is already the 31st.
        assertThat(manifest.get("timezone").asText()).isEqualTo("UTC");
        assertThat(manifest.get("firstDay").asText()).isEqualTo("2026-10-31");
    }
}
