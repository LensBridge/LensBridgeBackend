package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * {@code mbu.json} of a {@code content} package, format version 2. This is a wire contract
 * with the board's agent (MusallahBoard {@code agent/docs/architecture.md}, section 4.1), so
 * every field is spelled out as the exact JSON type the contract names: timestamps and dates
 * are plain strings, not whatever the ObjectMapper's date settings happen to produce.
 * <p>
 * The serialized bytes of this record are what gets signed. They are produced once, signed,
 * and stored in the zip as-is; nothing may re-serialize the manifest after signing.
 * {@link JsonInclude.Include#ALWAYS} keeps {@code "version": null} in the output even if the
 * application mapper is ever configured to drop nulls, so the bytes do not depend on that.
 *
 * @param format        always {@code "mbu"}
 * @param formatVersion always {@value OfflineBundleService#FORMAT_VERSION}
 * @param type          always {@code "content"}: this backend only holds a content key
 * @param createdAt     RFC 3339 UTC, second precision ({@code 2026-09-24T14:02:11Z})
 * @param sequence      build time in epoch milliseconds (not truncated like {@code createdAt});
 *                      the board's anti-rollback counter
 * @param deviceId      the device the content is bound to
 * @param version       always null for content (only software packages carry a version)
 * @param files         every payload and media file, including media left out of this zip
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"format", "formatVersion", "type", "createdAt", "sequence", "deviceId", "version", "files", "content"})
public record MbuManifest(
        String format,
        int formatVersion,
        String type,
        String createdAt,
        long sequence,
        String deviceId,
        String version,
        List<FileEntry> files,
        Content content
) {

    /** One file of the package. {@code sha256} is lowercase hex of the bytes, {@code bytes} their exact size. */
    @JsonPropertyOrder({"path", "sha256", "bytes"})
    public record FileEntry(String path, String sha256, long bytes) {}

    /**
     * The per-type block of a content package.
     *
     * @param timezone IANA zone the days were cut in
     * @param firstDay ISO date, inclusive
     * @param lastDay  ISO date, inclusive
     * @param media    every {@code media/} file with the type the board serves it as
     */
    @JsonPropertyOrder({"timezone", "firstDay", "lastDay", "media"})
    public record Content(String timezone, String firstDay, String lastDay, List<MediaEntry> media) {}

    @JsonPropertyOrder({"path", "contentType"})
    public record MediaEntry(String path, String contentType) {}
}
