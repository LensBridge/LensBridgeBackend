package com.ibrasoft.lensbridge.service.board.offline;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MediaMetadataCacheTest {

    /** A clock the test can move. */
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-24T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static MediaMetadataCache.MediaMetadata meta(String sha) {
        return new MediaMetadataCache.MediaMetadata("media/" + sha + ".jpg", sha, 10, "image/jpeg");
    }

    @Test
    void entriesExpireAfterTheTtl() {
        MutableClock clock = new MutableClock();
        MediaMetadataCache cache = new MediaMetadataCache(clock);
        cache.put("a.jpg", meta("aa"));

        clock.now = clock.now.plus(Duration.ofMinutes(60));
        assertThat(cache.get("a.jpg")).isEqualTo(meta("aa"));

        clock.now = clock.now.plusSeconds(1);
        assertThat(cache.get("a.jpg")).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    void theLeastRecentlyUsedEntryIsEvictedAtCapacity() {
        MediaMetadataCache cache = new MediaMetadataCache(2, Duration.ofHours(1), new MutableClock());
        cache.put("a.jpg", meta("aa"));
        cache.put("b.jpg", meta("bb"));
        cache.get("a.jpg"); // a is now more recent than b

        cache.put("c.jpg", meta("cc"));

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("b.jpg")).isNull();
        assertThat(cache.get("a.jpg")).isNotNull();
        assertThat(cache.get("c.jpg")).isNotNull();
    }
}
