package com.ibrasoft.lensbridge.service.board.offline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers, per storage object key, what a poster image turned into inside a package: its
 * SHA-256, bundle path, size and content type.
 * <p>
 * This is what makes online sync cheap. A board syncs every 30 minutes and after every
 * refresh, and almost always already holds every poster; it says so in {@code haveMedia}.
 * The manifest must still list those files with their hash and size, and without this cache
 * the only way to learn the hash is to download the image from R2 again. With it, a sync in
 * which the board has all media downloads nothing at all.
 * <p>
 * Bounded (least recently used entries go first) and short-lived. Poster images are
 * uploaded under fresh keys, so an entry going stale needs someone to overwrite an object in
 * place; the one-hour TTL caps how long a manifest could then describe the old bytes. Even
 * that is safe on the board: a file that is actually shipped is always downloaded and hashed
 * afresh (and refreshes the entry), and a listed file the board lacks makes it reject the
 * package rather than show something unsigned.
 */
final class MediaMetadataCache {

    static final int DEFAULT_MAX_ENTRIES = 1024;
    static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /** What a fetched image became; everything the manifest needs except the bytes themselves. */
    record MediaMetadata(String path, String sha256, long bytes, String contentType) {}

    private record Timestamped(MediaMetadata metadata, Instant storedAt) {}

    private final int maxEntries;
    private final Duration ttl;
    private final Clock clock;
    private final LinkedHashMap<String, Timestamped> entries;

    MediaMetadataCache(Clock clock) {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_TTL, clock);
    }

    MediaMetadataCache(int maxEntries, Duration ttl, Clock clock) {
        this.maxEntries = maxEntries;
        this.ttl = ttl;
        this.clock = clock;
        // Access order, so get() refreshes recency and the eldest entry is the least recently used.
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Timestamped> eldest) {
                return size() > MediaMetadataCache.this.maxEntries;
            }
        };
    }

    /** The cached metadata for {@code objectKey}, or null if absent or older than the TTL. */
    synchronized MediaMetadata get(String objectKey) {
        Timestamped entry = entries.get(objectKey);
        if (entry == null) return null;
        if (entry.storedAt().plus(ttl).isBefore(clock.instant())) {
            entries.remove(objectKey);
            return null;
        }
        return entry.metadata();
    }

    synchronized void put(String objectKey, MediaMetadata metadata) {
        entries.put(objectKey, new Timestamped(metadata, clock.instant()));
    }

    synchronized int size() {
        return entries.size();
    }
}
