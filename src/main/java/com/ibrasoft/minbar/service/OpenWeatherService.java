package com.ibrasoft.minbar.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin proxy around the OpenWeatherMap "current weather" API. The backend holds
 * the API key and forwards the upstream JSON response verbatim so clients (e.g.
 * the Musallah board) never see the key.
 *
 * <p>Weather changes slowly and must never sit in the board's request critical
 * path, so the upstream call runs on a background schedule (every ~10 min) and
 * the latest result is held in memory. {@link #getCurrentWeather()} is a
 * non-blocking field read that never fails — at worst it returns {@code null}
 * (no key configured, or before the first successful fetch).
 */
@Service
@Slf4j
public class OpenWeatherService {

    private final RestClient restClient;
    private final String apiKey;
    private final String location;
    private final String units;

    private volatile JsonNode currentWeather;

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

    /** Returns the most recently fetched weather, or {@code null} if none yet. */
    public JsonNode getCurrentWeather() {
        return currentWeather;
    }

    /**
     * Refreshes the cached weather from OpenWeatherMap. Runs on a fixed delay
     * (so a slow upstream call can't pile up overlapping requests) and swallows
     * all failures — a transient outage just leaves the last good value in
     * place rather than propagating to the board.
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
                currentWeather = response;
                log.debug("Refreshed weather for {}", location);
            }
        } catch (Exception e) {
            log.warn("Failed to refresh weather for {}: {} (keeping last known value)",
                    location, e.getMessage());
        }
    }
}
