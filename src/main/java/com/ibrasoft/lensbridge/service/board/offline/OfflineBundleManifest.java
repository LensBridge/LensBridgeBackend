package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.UUID;

/**
 * {@code manifest.json} inside an offline bundle. This is a wire contract with the board's
 * agent (see MusallahBoard {@code agent/docs/offline.md}, "Contract 1"), so dates are plain
 * strings in the exact format the contract names rather than whatever the ObjectMapper's
 * date settings happen to produce.
 *
 * @param generatedAt ISO-8601 UTC instant, second precision ({@code 2026-09-24T14:02:11Z})
 * @param firstDay    ISO date, inclusive
 * @param lastDay     ISO date, inclusive
 */
@JsonPropertyOrder({"formatVersion", "deviceId", "timezone", "generatedAt", "firstDay", "lastDay", "media"})
public record OfflineBundleManifest(
        int formatVersion,
        UUID deviceId,
        String timezone,
        String generatedAt,
        String firstDay,
        String lastDay,
        List<MediaEntry> media
) {

    /** One file under {@code media/}. {@code sha256} is lowercase hex and the file's base name. */
    @JsonPropertyOrder({"path", "sha256", "bytes", "contentType"})
    public record MediaEntry(String path, String sha256, long bytes, String contentType) {}
}
