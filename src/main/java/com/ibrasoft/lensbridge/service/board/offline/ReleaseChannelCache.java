package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The latest board app and arm64 agent packages, from the same GitHub release channels the
 * agent follows (agent {@code internal/config}, architecture section 9.4), for the USB kit.
 * <p>
 * A channel file is only a pointer ({@code {version, url, sha256, bytes}}). The backend checks
 * the download against its sha256 and size and passes the package on byte for byte; it never
 * signs software, and the board verifies the package against the release key compiled into its
 * agent, as it does for any other transport. So a tampered channel or cache can at worst put a
 * package on the stick that the board refuses.
 * <p>
 * A channel is checked at most every {@link #RECHECK}; the last good package is kept in memory
 * and served while GitHub cannot be reached. When there is none yet, {@link #latest} says why,
 * and the kit goes without it rather than failing.
 */
@Component
@Slf4j
public class ReleaseChannelCache {

    /** Default channels: the agent's own defaults (internal/config/config.go), arm64 for the Pi. */
    public static final String DEFAULT_APP_CHANNEL =
            "https://github.com/LensBridge/MusallahBoard/releases/latest/download/app-channel.json";
    public static final String DEFAULT_AGENT_CHANNEL =
            "https://github.com/LensBridge/agent/releases/latest/download/agent-channel-arm64.json";

    static final Duration RECHECK = Duration.ofMinutes(30);
    /** mbu.MaxPackageBytes: the most a board accepts. */
    static final long MAX_PACKAGE_BYTES = 512L << 20;
    private static final long MAX_CHANNEL_BYTES = 64 << 10;
    private static final Pattern VERSION = Pattern.compile("^(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    public enum Kind {
        APP("board app"), AGENT("agent");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** A package as the board will read it from the stick. */
    public record ReleasePackage(Kind kind, String version, String filename, byte[] bytes) {}

    /** Either a package, or why there is none (for the kit's notice). */
    public record Lookup(ReleasePackage pkg, String problem) {}

    private record Cached(ReleasePackage pkg, Instant checkedAt) {}

    private final Map<Kind, String> channels = new EnumMap<>(Kind.class);
    private final Map<Kind, Cached> cache = new EnumMap<>(Kind.class);
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock;

    @Autowired
    public ReleaseChannelCache(
            @Value("${musallahboard.release.app-channel-url:" + DEFAULT_APP_CHANNEL + "}") String appChannel,
            @Value("${musallahboard.release.agent-channel-url:" + DEFAULT_AGENT_CHANNEL + "}") String agentChannel) {
        this(appChannel, agentChannel, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), Clock.systemUTC());
    }

    ReleaseChannelCache(String appChannel, String agentChannel, HttpClient http, Clock clock) {
        channels.put(Kind.APP, appChannel == null ? "" : appChannel.trim());
        channels.put(Kind.AGENT, agentChannel == null ? "" : agentChannel.trim());
        this.http = http;
        this.clock = clock;
    }

    /** The latest package of this kind, from the cache when it was checked recently. */
    public synchronized Lookup latest(Kind kind) {
        String channel = channels.get(kind);
        if (channel.isEmpty()) {
            return new Lookup(null, "the " + kind.label + " release channel is turned off on this server");
        }
        Cached cached = cache.get(kind);
        Instant now = clock.instant();
        if (cached != null && cached.checkedAt().plus(RECHECK).isAfter(now)) {
            return new Lookup(cached.pkg(), null);
        }
        try {
            ReleasePackage pkg = fetch(kind, channel, cached == null ? null : cached.pkg());
            cache.put(kind, new Cached(pkg, now));
            return new Lookup(pkg, null);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String problem = "could not fetch the latest " + kind.label + " from " + channel + ": " + e.getMessage();
            if (cached != null) {
                log.warn("{}; using the cached {} {}", problem, kind.label, cached.pkg().version());
                cache.put(kind, new Cached(cached.pkg(), now)); // do not retry on every kit meanwhile
                return new Lookup(cached.pkg(), null);
            }
            log.warn("{}; the USB kit goes without it", problem);
            return new Lookup(null, problem);
        }
    }

    private ReleasePackage fetch(Kind kind, String channel, ReleasePackage current)
            throws IOException, InterruptedException {
        JsonNode pointer = mapper.readTree(get(channel, Duration.ofSeconds(15), MAX_CHANNEL_BYTES));
        String version = text(pointer, "version");
        String url = text(pointer, "url");
        String sha256 = text(pointer, "sha256");
        long bytes = pointer.path("bytes").asLong(-1);
        if (!VERSION.matcher(version).matches()) throw new IOException("channel version " + version + " is not MAJOR.MINOR.PATCH");
        if (!SHA256.matcher(sha256).matches()) throw new IOException("channel sha256 is malformed");
        if (bytes <= 0 || bytes > MAX_PACKAGE_BYTES) throw new IOException("channel size " + bytes + " is out of range");
        if (!url.startsWith("https://") && !url.startsWith("http://")) throw new IOException("channel url is not http(s)");

        if (current != null && current.version().equals(version)) return current;

        byte[] body = get(url, Duration.ofMinutes(2), bytes);
        if (body.length != bytes) throw new IOException("download is " + body.length + " bytes, the channel says " + bytes);
        if (!MbuPackager.sha256Hex(body).equals(sha256)) throw new IOException("download does not match the channel's sha256");

        String filename = kind == Kind.APP
                ? "musallahboard-app-" + version + ".mbu"
                : "musallahboard-agent-" + version + "-arm64.mbu";
        log.info("Fetched {} {} ({} bytes) for USB kits", kind.label, version, body.length);
        return new ReleasePackage(kind, version, filename, body);
    }

    private byte[] get(String url, Duration timeout, long limit) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            byte[] body = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
            if (body.length > limit) throw new IOException("larger than " + limit + " bytes");
            return body;
        }
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual()) throw new IOException("channel has no " + field);
        return v.asText();
    }
}
