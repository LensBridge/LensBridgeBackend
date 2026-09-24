package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.PosterFrameConfig;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.R2StorageService;
import com.ibrasoft.lensbridge.service.board.BoardPayloadAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the zip an admin carries to a board that has no internet: a manifest, one payload
 * per day, and every poster image those payloads reference. The format is a contract with the
 * board's agent — see MusallahBoard {@code agent/docs/offline.md}, "Contract 1".
 * <p>
 * Each day's payload comes from {@link BoardPayloadAssembler#assembleForDay}, so no frame
 * logic lives here. This class only fetches images, rewrites their URLs to bundle paths and
 * packs the result.
 * <p>
 * The whole bundle is built in memory before a byte is written. A bundle is a few days of
 * JSON plus a handful of posters (10 MB cap each), and building first is the only way a
 * failed image download can still become a 502 rather than a truncated download.
 */
@Service
@Slf4j
public class OfflineBundleService {

    public static final int FORMAT_VERSION = 1;
    public static final int DEFAULT_DAYS = 14;
    public static final int MAX_DAYS = 31;

    private static final String MEDIA_DIR = "media/";
    private static final String PAYLOAD_DIR = "payloads/";

    /** Content types we know an extension for. Anything else keeps the object key's extension. */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "image/avif", "avif",
            "image/svg+xml", "svg");

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif",
            "avif", "image/avif",
            "svg", "image/svg+xml");

    private final BoardPayloadAssembler payloadAssembler;
    private final DeviceRepository deviceRepository;
    private final R2StorageService r2StorageService;
    /** Spring's own mapper, so payload files serialize exactly like {@code /api/musallah/payload}. */
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OfflineBundleService(BoardPayloadAssembler payloadAssembler,
                                DeviceRepository deviceRepository,
                                R2StorageService r2StorageService,
                                ObjectMapper objectMapper) {
        this(payloadAssembler, deviceRepository, r2StorageService, objectMapper, Clock.systemUTC());
    }

    OfflineBundleService(BoardPayloadAssembler payloadAssembler,
                         DeviceRepository deviceRepository,
                         R2StorageService r2StorageService,
                         ObjectMapper objectMapper,
                         Clock clock) {
        this.payloadAssembler = payloadAssembler;
        this.deviceRepository = deviceRepository;
        this.r2StorageService = r2StorageService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param days number of days starting today in the device's timezone, 1–{@value #MAX_DAYS}
     * @throws ApiResponseException 400 for a bad {@code days}, 404 for an unknown device, 409
     *                              for a revoked one, 502 when a poster image cannot be fetched
     */
    public OfflineBundle build(UUID deviceId, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("days must be between 1 and " + MAX_DAYS));
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Device not found")));
        if (device.getRevokedAt() != null) {
            throw new ApiResponseException(HttpStatus.CONFLICT,
                    ErrorResponse.of("Device is revoked"));
        }

        ZoneId zone = payloadAssembler.zoneFor(device);
        Instant generatedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        LocalDate firstDay = LocalDate.ofInstant(generatedAt, zone);
        LocalDate lastDay = firstDay.plusDays(days - 1L);

        Map<LocalDate, MusallahBoardPayload> payloads = new LinkedHashMap<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            payloads.put(day, payloadAssembler.assembleForDay(device, day));
        }

        // A poster running all fortnight appears in every payload; download it once. Two
        // posters sharing identical bytes under different keys are stored once too.
        Map<String, MediaFile> mediaByHash = new LinkedHashMap<>();
        Map<String, MediaFile> mediaByUrl = new LinkedHashMap<>();
        for (MusallahBoardPayload payload : payloads.values()) {
            for (PosterFrame poster : posterFramesOf(payload)) {
                String url = poster.config().getPosterUrl();
                MediaFile media = url == null ? null : mediaByUrl.get(url);
                if (media == null) {
                    MediaFile fetched = fetch(poster);
                    media = mediaByHash.computeIfAbsent(fetched.sha256(), sha -> fetched);
                    mediaByUrl.put(url, media);
                }
                poster.config().setPosterUrl("/" + media.path());
            }
        }

        List<OfflineBundle.Entry> entries = new ArrayList<>();
        entries.add(new OfflineBundle.Entry("manifest.json",
                toJson(manifest(deviceId, zone, generatedAt, firstDay, lastDay, mediaByHash.values())),
                false));
        payloads.forEach((day, payload) ->
                entries.add(new OfflineBundle.Entry(PAYLOAD_DIR + day + ".json", toJson(payload), false)));
        mediaByHash.values().forEach(media ->
                entries.add(new OfflineBundle.Entry(media.path(), media.content(), true)));

        log.info("Built offline bundle for device {}: {}..{}, {} media file(s)",
                deviceId, firstDay, lastDay, mediaByHash.size());

        String filename = "musallahboard-" + deviceId.toString().substring(0, 8) + "-" + firstDay + ".zip";
        return new OfflineBundle(filename, entries);
    }

    private static OfflineBundleManifest manifest(UUID deviceId, ZoneId zone, Instant generatedAt,
                                                  LocalDate firstDay, LocalDate lastDay,
                                                  Iterable<MediaFile> media) {
        List<OfflineBundleManifest.MediaEntry> entries = new ArrayList<>();
        for (MediaFile m : media) {
            entries.add(new OfflineBundleManifest.MediaEntry(
                    m.path(), m.sha256(), m.content().length, m.contentType()));
        }
        return new OfflineBundleManifest(
                FORMAT_VERSION,
                deviceId,
                zone.getId(),
                generatedAt.toString(),
                firstDay.toString(),
                lastDay.toString(),
                entries);
    }

    // ==================== Media ====================

    private record PosterFrame(String frameId, PosterFrameConfig config) {}

    private record MediaFile(String path, String sha256, byte[] content, String contentType) {}

    private static List<PosterFrame> posterFramesOf(MusallahBoardPayload payload) {
        List<PosterFrame> posters = new ArrayList<>();
        if (payload.getFrames() == null) return posters;
        for (FrameDefinition frame : payload.getFrames()) {
            if (frame.getFrameConfig() instanceof PosterFrameConfig config) {
                posters.add(new PosterFrame(frame.getFrameId(), config));
            }
        }
        return posters;
    }

    /** Downloads one poster's image. Any failure is a 502 naming the poster. */
    private MediaFile fetch(PosterFrame poster) {
        String url = poster.config().getPosterUrl();
        if (url == null || url.isBlank()) {
            throw imageFailure(poster, "it has no image");
        }

        String objectKey = r2StorageService.objectKeyFromPublicUrl(url);
        if (objectKey == null || objectKey.isBlank()) {
            throw imageFailure(poster, "its image URL is not a storage object: " + url);
        }

        R2StorageService.R2Object object;
        try {
            object = r2StorageService.getObject(objectKey);
        } catch (Exception e) {
            log.error("Offline bundle: failed to fetch image {} for {}", objectKey, poster.frameId(), e);
            throw imageFailure(poster, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (object == null || object.bytes() == null || object.bytes().length == 0) {
            throw imageFailure(poster, "its image is empty");
        }

        String sha256 = sha256Hex(object.bytes());
        String keyExtension = extensionOf(objectKey);
        String contentType = normalizeContentType(object.contentType());
        // Trust a stored image type; otherwise (missing, or a generic type such as
        // application/octet-stream) go by the key's extension. Map.of rejects null keys on get().
        String extension = contentType == null ? null : EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            extension = keyExtension != null ? keyExtension : "bin";
            String byExtension = CONTENT_TYPE_BY_EXTENSION.get(extension);
            if (byExtension != null || contentType == null) {
                contentType = byExtension != null ? byExtension : "application/octet-stream";
            }
        }

        return new MediaFile(MEDIA_DIR + sha256 + "." + extension, sha256, object.bytes(), contentType);
    }

    private static ApiResponseException imageFailure(PosterFrame poster, String reason) {
        String title = poster.config().getTitle();
        String name = title == null || title.isBlank()
                ? poster.frameId()
                : "\"" + title + "\" (" + poster.frameId() + ")";
        return new ApiResponseException(HttpStatus.BAD_GATEWAY,
                ErrorResponse.of("Could not fetch the image for poster " + name + ": " + reason));
    }

    /** Lowercase, parameters stripped; null when absent. */
    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return null;
        int semicolon = contentType.indexOf(';');
        String bare = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return bare.trim().toLowerCase(Locale.ROOT);
    }

    /** The key's extension if it is a plausible one (short, alphanumeric), lowercased. */
    private static String extensionOf(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        int dot = objectKey.lastIndexOf('.');
        if (dot <= slash + 1 || dot == objectKey.length() - 1) return null;
        String ext = objectKey.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.length() <= 5 && ext.chars().allMatch(Character::isLetterOrDigit) ? ext : null;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    private byte[] toJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new ApiResponseException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorResponse.of("Could not serialize offline bundle: " + e.getOriginalMessage()));
        }
    }
}
