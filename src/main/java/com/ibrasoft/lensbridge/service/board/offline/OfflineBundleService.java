package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds signed {@code content} packages ({@code .mbu}, format version 2) for a board: a
 * manifest, its signature, one payload per day, and the poster images those payloads
 * reference. The format is a contract with the board's agent; see MusallahBoard
 * {@code agent/docs/architecture.md}, section 4.
 * <p>
 * The same package serves every transport. An admin downloads it for a board with no
 * internet (all media included), and an online board fetches it itself through
 * {@code POST /api/agent/content-bundle}, naming the media it already holds so those files
 * are left out of the zip. Left-out files stay listed and signed in {@code mbu.json}, so the
 * board can check its own copies against them.
 * <p>
 * Each day's payload comes from {@link BoardPayloadAssembler#assembleForDay}, so no frame
 * logic lives here. This class only fetches images, rewrites their URLs to package paths,
 * writes the manifest, signs it and packs the result.
 * <p>
 * The whole package is built in memory before a byte is written. It is a few days of JSON
 * plus a handful of posters (10 MB cap each), and building first is the only way a failed
 * image download can still become a 502 rather than a truncated download.
 * <p>
 * Signing: the manifest is serialized exactly once. Those bytes are signed and those same
 * bytes are stored as {@code mbu.json}; the signature covers
 * {@value ContentSigningService#MBU_SIGNATURE_PREFIX} followed by them, and nothing else.
 */
@Service
@Slf4j
public class OfflineBundleService {

    public static final int FORMAT_VERSION = 2;
    public static final int DEFAULT_DAYS = 14;
    public static final int MAX_DAYS = 31;

    /** Media type of a {@code .mbu} package. */
    public static final String MBU_CONTENT_TYPE = "application/vnd.musallahboard.mbu";

    private static final String MANIFEST_ENTRY = "mbu.json";
    private static final String SIGNATURE_ENTRY = "mbu.sig";
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

    /**
     * The only content types the agent accepts in {@code content.media}. Anything else that
     * storage reports (an uploaded HEIC, say) is listed as {@code application/octet-stream}
     * rather than making the board reject the whole package.
     */
    private static final String OCTET_STREAM = "application/octet-stream";

    private final BoardPayloadAssembler payloadAssembler;
    private final DeviceRepository deviceRepository;
    private final R2StorageService r2StorageService;
    /** Spring's own mapper, so payload files serialize exactly like {@code /api/musallah/payload}. */
    private final ObjectMapper objectMapper;
    private final ContentSigningService signingService;
    private final Clock clock;
    /** Lets a sync in which the board already holds every poster skip downloading them. */
    private final MediaMetadataCache mediaMetadataCache;

    @Autowired
    public OfflineBundleService(BoardPayloadAssembler payloadAssembler,
                                DeviceRepository deviceRepository,
                                R2StorageService r2StorageService,
                                ObjectMapper objectMapper,
                                ContentSigningService signingService) {
        this(payloadAssembler, deviceRepository, r2StorageService, objectMapper, signingService,
                Clock.systemUTC());
    }

    OfflineBundleService(BoardPayloadAssembler payloadAssembler,
                         DeviceRepository deviceRepository,
                         R2StorageService r2StorageService,
                         ObjectMapper objectMapper,
                         ContentSigningService signingService,
                         Clock clock) {
        this.payloadAssembler = payloadAssembler;
        this.deviceRepository = deviceRepository;
        this.r2StorageService = r2StorageService;
        this.objectMapper = objectMapper;
        this.signingService = signingService;
        this.clock = clock;
        this.mediaMetadataCache = new MediaMetadataCache(clock);
    }

    /**
     * A package with every media file included, as an admin downloads it for a board with no
     * internet.
     *
     * @see #build(UUID, int, Set)
     */
    public OfflineBundle build(UUID deviceId, int days) {
        return build(deviceId, days, Set.of());
    }

    /**
     * @param days      number of days starting today in the device's timezone, 1-{@value #MAX_DAYS}
     * @param haveMedia SHA-256 (lowercase hex) of media the board already holds. Matching files
     *                  are listed in {@code mbu.json} but left out of the zip, and are not
     *                  downloaded from storage when their hash is already known.
     * @throws ApiResponseException 400 for a bad {@code days}, 404 for an unknown device, 409
     *                              for a revoked one, 503 when no content signing key is
     *                              configured, 502 when a poster image cannot be fetched
     */
    public OfflineBundle build(UUID deviceId, int days, Set<String> haveMedia) {
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
        // Before any assembly or download: without a key the result could not be signed anyway.
        signingService.requireConfigured();

        ZoneId zone = payloadAssembler.zoneFor(device);
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        LocalDate firstDay = LocalDate.ofInstant(createdAt, zone);
        LocalDate lastDay = firstDay.plusDays(days - 1L);

        Map<LocalDate, MusallahBoardPayload> payloads = new LinkedHashMap<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            payloads.put(day, payloadAssembler.assembleForDay(device, day));
        }

        // A poster running all fortnight appears in every payload; resolve it once. Two
        // posters sharing identical bytes under different keys are stored once too.
        Map<String, MediaFile> mediaByHash = new LinkedHashMap<>();
        Map<String, MediaFile> mediaByUrl = new LinkedHashMap<>();
        for (MusallahBoardPayload payload : payloads.values()) {
            for (PosterFrame poster : posterFramesOf(payload)) {
                String url = poster.config().getPosterUrl();
                MediaFile media = url == null ? null : mediaByUrl.get(url);
                if (media == null) {
                    MediaFile resolved = resolve(poster, haveMedia);
                    media = mediaByHash.computeIfAbsent(resolved.sha256(), sha -> resolved);
                    mediaByUrl.put(url, media);
                }
                poster.config().setPosterUrl("/" + media.path());
            }
        }

        // Payload bytes first: the manifest lists their hashes.
        Map<String, byte[]> payloadFiles = new LinkedHashMap<>();
        payloads.forEach((day, payload) -> payloadFiles.put(PAYLOAD_DIR + day + ".json", toJson(payload)));

        List<MbuManifest.FileEntry> files = new ArrayList<>();
        payloadFiles.forEach((path, bytes) -> files.add(new MbuManifest.FileEntry(path, sha256Hex(bytes), bytes.length)));
        List<MbuManifest.MediaEntry> mediaEntries = new ArrayList<>();
        for (MediaFile m : mediaByHash.values()) {
            files.add(new MbuManifest.FileEntry(m.path(), m.sha256(), m.bytes()));
            mediaEntries.add(new MbuManifest.MediaEntry(m.path(), m.contentType()));
        }

        MbuManifest manifest = new MbuManifest(
                "mbu",
                FORMAT_VERSION,
                "content",
                createdAt.toString(),
                createdAt.toEpochMilli(),
                deviceId.toString(),
                null,
                files,
                new MbuManifest.Content(zone.getId(), firstDay.toString(), lastDay.toString(), mediaEntries));

        // Serialize once; sign these bytes; store these same bytes.
        byte[] manifestBytes = toJson(manifest);
        byte[] signature = signingService.signManifest(manifestBytes);
        byte[] signatureFile = toJson(new SignatureFile(List.of(
                new SignatureEntry(signingService.currentKeyId(), Base64.getEncoder().encodeToString(signature)))));

        List<OfflineBundle.Entry> entries = new ArrayList<>();
        entries.add(new OfflineBundle.Entry(MANIFEST_ENTRY, manifestBytes, false));
        entries.add(new OfflineBundle.Entry(SIGNATURE_ENTRY, signatureFile, false));
        payloadFiles.forEach((path, bytes) -> entries.add(new OfflineBundle.Entry(path, bytes, false)));
        int omitted = 0;
        for (MediaFile media : mediaByHash.values()) {
            if (media.content() == null) {
                omitted++;
            } else {
                entries.add(new OfflineBundle.Entry(media.path(), media.content(), true));
            }
        }

        log.info("Built content package {} for device {}: {}..{}, {} media file(s), {} left out as already on the board",
                manifest.sequence(), deviceId, firstDay, lastDay, mediaByHash.size(), omitted);

        String filename = "musallahboard-content-" + deviceId.toString().substring(0, 8) + "-" + firstDay + ".mbu";
        return new OfflineBundle(filename, entries);
    }

    /** {@code mbu.sig}: {@code {"signatures":[{"keyId","sig"}]}}. */
    private record SignatureFile(List<SignatureEntry> signatures) {}

    @JsonPropertyOrder({"keyId", "sig"})
    private record SignatureEntry(String keyId, String sig) {}

    // ==================== Media ====================

    private record PosterFrame(String frameId, PosterFrameConfig config) {}

    /**
     * @param content the image bytes, or null when the board already holds this file and it
     *                is only listed in the manifest
     */
    private record MediaFile(String path, String sha256, long bytes, String contentType, byte[] content) {

        static MediaFile listedOnly(MediaMetadataCache.MediaMetadata metadata) {
            return new MediaFile(metadata.path(), metadata.sha256(), metadata.bytes(), metadata.contentType(), null);
        }
    }

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

    /**
     * The media file for one poster. When the board already holds it and its hash is cached,
     * nothing is downloaded; otherwise the image is fetched (which also refreshes the cache)
     * and its bytes are kept only if the board lacks them.
     */
    private MediaFile resolve(PosterFrame poster, Set<String> haveMedia) {
        String objectKey = objectKeyOf(poster);
        MediaMetadataCache.MediaMetadata cached = mediaMetadataCache.get(objectKey);
        if (cached != null && haveMedia.contains(cached.sha256())) {
            return MediaFile.listedOnly(cached);
        }
        MediaFile fetched = fetch(poster, objectKey);
        return haveMedia.contains(fetched.sha256())
                ? new MediaFile(fetched.path(), fetched.sha256(), fetched.bytes(), fetched.contentType(), null)
                : fetched;
    }

    /** The storage key behind a poster's image URL. Anything unusable is a 502 naming the poster. */
    private String objectKeyOf(PosterFrame poster) {
        String url = poster.config().getPosterUrl();
        if (url == null || url.isBlank()) {
            throw imageFailure(poster, "it has no image");
        }

        String objectKey = r2StorageService.objectKeyFromPublicUrl(url);
        if (objectKey == null || objectKey.isBlank()) {
            throw imageFailure(poster, "its image URL is not a storage object: " + url);
        }
        return objectKey;
    }

    /** Downloads one poster's image and records what it became. Any failure is a 502 naming the poster. */
    private MediaFile fetch(PosterFrame poster, String objectKey) {
        R2StorageService.R2Object object;
        try {
            object = r2StorageService.getObject(objectKey);
        } catch (Exception e) {
            log.error("Content package: failed to fetch image {} for {}", objectKey, poster.frameId(), e);
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
            // Whatever storage said, the manifest may only name a type the agent accepts.
            contentType = CONTENT_TYPE_BY_EXTENSION.getOrDefault(extension, OCTET_STREAM);
        }

        MediaMetadataCache.MediaMetadata metadata = new MediaMetadataCache.MediaMetadata(
                MEDIA_DIR + sha256 + "." + extension, sha256, object.bytes().length, contentType);
        mediaMetadataCache.put(objectKey, metadata);
        return new MediaFile(metadata.path(), sha256, metadata.bytes(), contentType, object.bytes());
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

    /**
     * The key's extension if it is a plausible one (at most five ASCII letters or digits),
     * lowercased. ASCII only: the agent requires media extensions to match {@code [a-z0-9]{1,10}}.
     */
    private static String extensionOf(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        int dot = objectKey.lastIndexOf('.');
        if (dot <= slash + 1 || dot == objectKey.length() - 1) return null;
        String ext = objectKey.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.length() <= 5 && ext.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
                ? ext : null;
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
                    ErrorResponse.of("Could not serialize content package: " + e.getOriginalMessage()));
        }
    }
}
