package com.ibrasoft.lensbridge.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The weather the board is handed must be dated by when it was fetched and must disappear once
 * it is too old to trust. Runs against a throwaway local HTTP server so the real RestClient path
 * is exercised, including the failure path.
 */
class OpenWeatherServiceTest {

    private static final String API_KEY = "s3cr3t-key";
    private static final String BODY = "{\"name\":\"Mississauga\",\"main\":{\"temp\":12.5}}";

    private HttpServer server;
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 200 ? bytes.length : -1);
            if (status == 200) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OpenWeatherService service(String apiKey) {
        return new OpenWeatherService(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/", apiKey, "Mississauga,ON,CA", "metric");
    }

    @Test
    void thereIsNoObservationBeforeTheFirstFetch() {
        assertThat(service(API_KEY).getCurrentObservation()).isNull();
    }

    @Test
    void anObservationCarriesTheTimeItWasFetched() {
        OpenWeatherService service = service(API_KEY);
        Instant before = Instant.now();

        service.refreshWeather();

        OpenWeatherService.Observation observation = service.getCurrentObservation();
        assertThat(observation).isNotNull();
        assertThat(observation.weather().get("name").asText()).isEqualTo("Mississauga");
        assertThat(observation.fetchedAt()).isBetween(before, Instant.now());
    }

    /** The fetch time must not move just because someone asked again later. */
    @Test
    void fetchedAtDoesNotChangeBetweenReads() {
        OpenWeatherService service = service(API_KEY);
        service.refreshWeather();

        Instant first = service.getCurrentObservation().fetchedAt();
        Instant later = service.currentObservation(first.plus(Duration.ofHours(1))).fetchedAt();

        assertThat(later).isEqualTo(first);
    }

    @Test
    void anObservationIsStillServedJustInsideTheMaximumAge() {
        OpenWeatherService service = service(API_KEY);
        service.refreshWeather();
        Instant fetched = service.getCurrentObservation().fetchedAt();

        assertThat(service.currentObservation(fetched.plus(OpenWeatherService.MAX_AGE))).isNotNull();
    }

    @Test
    void anObservationOlderThanThreeHoursIsDropped() {
        OpenWeatherService service = service(API_KEY);
        service.refreshWeather();
        Instant fetched = service.getCurrentObservation().fetchedAt();

        assertThat(OpenWeatherService.MAX_AGE).isEqualTo(Duration.ofHours(3));
        assertThat(service.currentObservation(fetched.plus(OpenWeatherService.MAX_AGE).plusSeconds(1))).isNull();
    }

    @Test
    void aFailedRefreshKeepsTheLastGoodObservationAndItsOriginalFetchTime() {
        OpenWeatherService service = service(API_KEY);
        service.refreshWeather();
        Instant fetched = service.getCurrentObservation().fetchedAt();

        status = 500;
        service.refreshWeather();

        assertThat(service.getCurrentObservation().fetchedAt()).isEqualTo(fetched);
    }

    @Test
    void withoutAnApiKeyNothingIsFetched() {
        OpenWeatherService service = service("");

        service.refreshWeather();

        assertThat(service.getCurrentObservation()).isNull();
    }

    // ==================== redaction ====================

    @Test
    void redactRemovesTheAppidQueryParameterFromAClientErrorMessage() {
        String message = "I/O error on GET request for \"http://api.example.com/weather?q=X&units=metric&appid="
                + API_KEY + "\": Connection refused";

        String redacted = OpenWeatherService.redact(message, API_KEY);

        assertThat(redacted).doesNotContain(API_KEY);
        assertThat(redacted).contains("appid=***").contains("Connection refused").contains("units=metric");
    }

    @Test
    void redactAlsoRemovesTheKeyWhereItAppearsWithoutTheParameterName() {
        assertThat(OpenWeatherService.redact("bad key " + API_KEY + " rejected", API_KEY))
                .doesNotContain(API_KEY);
    }

    @Test
    void redactHandlesAppidInTheMiddleAndAnUppercaseName() {
        assertThat(OpenWeatherService.redact("GET ?APPID=abc123&q=Toronto failed", "abc123"))
                .isEqualTo("GET ?APPID=***&q=Toronto failed");
    }

    @Test
    void redactPassesNullAndKeylessMessagesThrough() {
        assertThat(OpenWeatherService.redact(null, API_KEY)).isNull();
        assertThat(OpenWeatherService.redact("plain failure", null)).isEqualTo("plain failure");
    }
}
