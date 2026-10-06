package com.ibrasoft.lensbridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;

/**
 * Thin proxy around the OpenWeatherMap "current weather" API. The backend holds
 * the API key and forwards the upstream JSON response verbatim so clients (e.g.
 * the Musallah board) never see the key.
 *
 * <p>Weather changes slowly, so the upstream call runs on a background schedule
 * (every ~10 min) and the latest result is held in memory together with the
 * moment it was fetched. {@code GET /api/agent/weather} answers from that cache
 * through {@link #getCurrentObservation()}, a non-blocking field read that never
 * fails and never calls OpenWeatherMap itself. At worst it returns {@code null}
 * (no key configured, no successful fetch yet, or the last good fetch is older
 * than {@link #MAX_AGE}), so a long outage hides the board's weather widget
 * rather than showing an observation hours old as if it were current.
 */
@Service
@Slf4j
public class OpenWeatherService {

    private final RestClient restClient;
    private final String apiKey;
    private final String location;
    private final String units;

    /**
     * How long a fetched observation is still worth showing. Refreshes run every ~10 minutes,
     * so this tolerates a few missed ones while keeping a prolonged outage from presenting a
     * stale temperature as fresh.
     */
    static final Duration MAX_AGE = Duration.ofHours(3);

    /** A fetched response and when it was fetched. */
    public record Observation(JsonNode weather, Instant fetchedAt) {}

    private volatile Observation latest;

    public OpenWeatherService(
            @Value("${openweather.api.url:https://api.openweathermap.org/data/2.5/weather}") String apiUrl,
            @Value("${openweather.api.key:}") String apiKey,
            @Value("${openweather.location:Mississauga,ON,CA}") String location,
            @Value("${openweather.units:metric}") String units) {
        this.restClient = RestClient.create(apiUrl);
        this.apiKey = apiKey;
        this.location = location;
        this.units = units;
    }

    /**
     * The most recent successful fetch, or {@code null} if there is none or it is older than
     * {@link #MAX_AGE}.
     */
    public Observation getCurrentObservation() {
        return currentObservation(Instant.now());
    }

    Observation currentObservation(Instant now) {
        Observation observation = latest;
        if (observation == null) {
            return null;
        }
        return Duration.between(observation.fetchedAt(), now).compareTo(MAX_AGE) > 0
                ? null
                : observation;
    }

    /**
     * Refreshes the cached weather from OpenWeatherMap. Runs on a fixed delay
     * (so a slow upstream call can't pile up overlapping requests) and swallows
     * all failures. A transient outage leaves the last good value in place
     * (until it ages past {@link #MAX_AGE}) rather than propagating to the board.
     */
    @Scheduled(fixedDelayString = "${openweather.refresh-interval-ms:600000}")
    void refreshWeather() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenWeather API key not configured; skipping weather refresh");
            return;
        }
        try {
            String uri = UriComponentsBuilder.newInstance()
                    .queryParam("q", location)
                    .queryParam("units", units)
                    .queryParam("appid", apiKey)
                    .build()
                    .toUriString();
            JsonNode response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null) {
                latest = new Observation(response, Instant.now());
                log.debug("Refreshed weather for {}", location);
            }
        } catch (Exception e) {
            log.warn("Failed to refresh weather for {}: {} (keeping last known value)",
                    location, redact(e.getMessage(), apiKey));
        }
    }

    /**
     * Client exceptions quote the request URL, and the API key travels in its {@code appid}
     * query parameter, so the message has to be scrubbed before it reaches a log.
     */
    static String redact(String message, String apiKey) {
        if (message == null) {
            return null;
        }
        String redacted = message.replaceAll("(?i)(appid=)[^&\\s\"')]*", "$1***");
        if (apiKey != null && !apiKey.isBlank()) {
            redacted = redacted.replace(apiKey, "***");
        }
        return redacted;
    }
}
