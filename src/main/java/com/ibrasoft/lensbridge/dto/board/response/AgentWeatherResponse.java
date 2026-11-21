package com.ibrasoft.lensbridge.dto.board.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * {@code GET /api/agent/weather}. Weather is the one thing a board still fetches live: it
 * cannot go into a content package built days ahead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentWeatherResponse {

    /**
     * The OpenWeatherMap "current weather" response, forwarded verbatim, or null when the
     * server has none (no API key, no successful fetch yet, the last fetch is over 3 hours old, or
     * the weather service failed).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "OpenWeatherMap current weather JSON, verbatim; null when unavailable")
    private JsonNode weather;

    /**
     * When the server fetched {@link #weather} from OpenWeatherMap, as a UTC instant, so a board
     * can judge how old the observation is. When {@code weather} is null there is nothing to
     * date, and this is when the server answered.
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-24T14:05:00Z",
            description = "When the server fetched the weather from OpenWeatherMap; when weather is null, "
                    + "when the server answered")
    private Instant fetchedAt;
}
