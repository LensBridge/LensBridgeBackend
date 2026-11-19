package com.ibrasoft.lensbridge.service.board.offline;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The release channels as GitHub serves them, from a local HTTP server. */
class ReleaseChannelCacheTest {

    private static final byte[] PACKAGE = "pretend this is a signed app .mbu".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String base;
    private volatile String channelJson;
    private volatile boolean up = true;
    private final AtomicInteger packageDownloads = new AtomicInteger();
    private Instant now = Instant.parse("2026-11-19T12:00:00Z");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/channel.json", ex -> respond(ex, channelJson.getBytes(StandardCharsets.UTF_8)));
        // Like GitHub's releases/latest/download: a redirect to where the asset really is.
        server.createContext("/latest/app.mbu", ex -> {
            ex.getResponseHeaders().add("Location", base + "/assets/app.mbu");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/assets/app.mbu", ex -> {
            packageDownloads.incrementAndGet();
            respond(ex, PACKAGE);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        channelJson = channel("2.1.0", PACKAGE.length, sha(PACKAGE));
    }

    private void respond(com.sun.net.httpserver.HttpExchange ex, byte[] body) throws IOException {
        if (!up) {
            ex.sendResponseHeaders(503, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String channel(String version, long bytes, String sha) {
        return "{\"version\":\"" + version + "\",\"url\":\"" + base + "/latest/app.mbu\",\"sha256\":\"" + sha
                + "\",\"bytes\":" + bytes + "}";
    }

    private static String sha(byte[] b) {
        return MbuPackager.sha256Hex(b);
    }

    private ReleaseChannelCache cache(String appChannel) {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        return new ReleaseChannelCache(appChannel, "", HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL).build(), clock);
    }

    @Test
    void fetchesThePackageTheChannelNamesAndNamesItByVersion() {
        ReleaseChannelCache.Lookup lookup = cache(base + "/channel.json").latest(ReleaseChannelCache.Kind.APP);

        assertThat(lookup.problem()).isNull();
        assertThat(lookup.pkg().version()).isEqualTo("2.1.0");
        assertThat(lookup.pkg().filename()).isEqualTo("musallahboard-app-2.1.0.mbu");
        assertThat(lookup.pkg().bytes()).isEqualTo(PACKAGE);
    }

    @Test
    void aDownloadThatDoesNotMatchTheChannelIsRefused() {
        channelJson = channel("2.1.0", PACKAGE.length, "0".repeat(64));
        ReleaseChannelCache.Lookup lookup = cache(base + "/channel.json").latest(ReleaseChannelCache.Kind.APP);
        assertThat(lookup.pkg()).isNull();
        assertThat(lookup.problem()).contains("sha256");

        channelJson = channel("2.1.0", PACKAGE.length + 1, sha(PACKAGE));
        assertThat(cache(base + "/channel.json").latest(ReleaseChannelCache.Kind.APP).problem()).contains("bytes");
    }

    @Test
    void unreachableWithNothingCachedSaysWhy() {
        up = false;
        ReleaseChannelCache.Lookup lookup = cache(base + "/channel.json").latest(ReleaseChannelCache.Kind.APP);
        assertThat(lookup.pkg()).isNull();
        assertThat(lookup.problem()).contains("board app").contains("HTTP 503");
    }

    @Test
    void theLastGoodPackageIsServedWhileGitHubIsDown() {
        ReleaseChannelCache cache = cache(base + "/channel.json");
        cache.latest(ReleaseChannelCache.Kind.APP);
        up = false;
        now = now.plus(Duration.ofHours(2));

        ReleaseChannelCache.Lookup lookup = cache.latest(ReleaseChannelCache.Kind.APP);
        assertThat(lookup.pkg().version()).isEqualTo("2.1.0");
    }

    @Test
    void checksAtMostEveryHalfHourAndDownloadsOnlyANewVersion() {
        ReleaseChannelCache cache = cache(base + "/channel.json");
        cache.latest(ReleaseChannelCache.Kind.APP);
        cache.latest(ReleaseChannelCache.Kind.APP);
        now = now.plus(Duration.ofMinutes(31));
        cache.latest(ReleaseChannelCache.Kind.APP);
        assertThat(packageDownloads.get()).as("same version: not downloaded again").isEqualTo(1);

        byte[] next = "2.2.0 package".getBytes(StandardCharsets.UTF_8);
        server.removeContext("/assets/app.mbu");
        server.createContext("/assets/app.mbu", ex -> respond(ex, next));
        channelJson = channel("2.2.0", next.length, sha(next));
        now = now.plus(Duration.ofMinutes(31));
        assertThat(cache.latest(ReleaseChannelCache.Kind.APP).pkg().filename()).isEqualTo("musallahboard-app-2.2.0.mbu");
    }

    @Test
    void aChannelSetToEmptyIsOff() {
        ReleaseChannelCache.Lookup lookup = cache("").latest(ReleaseChannelCache.Kind.AGENT);
        assertThat(lookup.pkg()).isNull();
        assertThat(lookup.problem()).contains("turned off");
    }

    @Test
    void theDefaultsAreTheAgentsChannels() {
        assertThat(ReleaseChannelCache.DEFAULT_APP_CHANNEL)
                .isEqualTo("https://github.com/LensBridge/MusallahBoard/releases/latest/download/app-channel.json");
        assertThat(ReleaseChannelCache.DEFAULT_AGENT_CHANNEL)
                .isEqualTo("https://github.com/LensBridge/agent/releases/latest/download/agent-channel-arm64.json");
    }
}
