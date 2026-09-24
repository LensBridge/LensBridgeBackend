package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.OpenWeatherService;
import com.ibrasoft.lensbridge.service.board.producer.FrameProducer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the assembler's fault isolation: a broken producer costs the board its own slides and
 * nothing else. See {@code produceSafely}.
 */
class BoardPayloadAssemblerTest {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Toronto");
    private static final UUID DEVICE_ID = UUID.randomUUID();

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final OpenWeatherService weatherService = mock(OpenWeatherService.class);

    private BoardPayloadAssembler assemblerWith(FrameProducer... producers) {
        Device device = Device.builder()
                .id(DEVICE_ID)
                .displayName("test-board")
                .audience(Audience.BOTH)
                .build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));

        return new BoardPayloadAssembler(
                deviceRepository, weatherService, List.of(producers), DEFAULT_ZONE);
    }

    /** A producer that emits one frame carrying the given id, so order is checkable. */
    private static FrameProducer emitting(String frameId) {
        return ctx -> List.of(FrameDefinition.builder()
                .frameId(frameId)
                .frameType(FrameType.POSTER)
                .durationInSeconds(10)
                .frameConfig(null)
                .build());
    }

    private static FrameProducer throwing(RuntimeException e) {
        return ctx -> {
            throw e;
        };
    }

    private static List<String> frameIdsOf(MusallahBoardPayload payload) {
        return payload.getFrames().stream().map(FrameDefinition::getFrameId).toList();
    }

    @Test
    void aFailingProducerIsSkippedAndTheRestOfThePayloadSurvives() {
        BoardPayloadAssembler assembler = assemblerWith(
                emitting("first"),
                throwing(new IllegalStateException("poster query blew up")),
                emitting("third"));

        MusallahBoardPayload payload = assembler.assemble(DEVICE_ID);

        assertThat(frameIdsOf(payload)).containsExactly("first", "third");
    }

    /** Producers run in injected order; dropping one must not reshuffle the survivors. */
    @Test
    void survivingFramesKeepTheirProducerOrder() {
        BoardPayloadAssembler assembler = assemblerWith(
                throwing(new RuntimeException("boom")),
                emitting("a"),
                emitting("b"),
                throwing(new RuntimeException("boom")),
                emitting("c"));

        assertThat(frameIdsOf(assembler.assemble(DEVICE_ID))).containsExactly("a", "b", "c");
    }

    /** A null return is a producer bug, not a reason to blank the board. */
    @Test
    void aProducerReturningNullIsTreatedAsEmpty() {
        BoardPayloadAssembler assembler = assemblerWith(ctx -> null, emitting("only"));

        assertThat(frameIdsOf(assembler.assemble(DEVICE_ID))).containsExactly("only");
    }

    /**
     * Total producer failure still yields a payload. The board gets its config and can render
     * its own empty state, which beats a 500 it has no way to display.
     */
    @Test
    void everyProducerFailingStillReturnsAPayload() {
        BoardPayloadAssembler assembler = assemblerWith(
                throwing(new RuntimeException("boom")),
                throwing(new IllegalArgumentException("also boom")));

        MusallahBoardPayload payload = assembler.assemble(DEVICE_ID);

        assertThat(payload).isNotNull();
        assertThat(payload.getFrames()).isEmpty();
    }

    /**
     * An {@link Error} is not a missing slide. Swallowing it would log an OOM as a skipped
     * frame and keep serving payloads from a JVM that should be failing loudly.
     */
    @Test
    void errorsStillPropagate() {
        BoardPayloadAssembler assembler = assemblerWith(ctx -> {
            throw new StackOverflowError("recursion in a producer");
        });

        assertThatThrownBy(() -> assembler.assemble(DEVICE_ID))
                .isInstanceOf(StackOverflowError.class);
    }

    /** Degradation is for producers only — an unknown device is still a 404. */
    @Test
    void anUnknownDeviceStillFails() {
        UUID missing = UUID.randomUUID();
        when(deviceRepository.findById(any())).thenReturn(Optional.empty());

        BoardPayloadAssembler assembler = new BoardPayloadAssembler(
                deviceRepository, weatherService, List.of(emitting("unused")), DEFAULT_ZONE);

        assertThatThrownBy(() -> assembler.assemble(missing))
                .isInstanceOf(ApiResponseException.class);
    }

    // ==================== assembleForDay ====================

    private static Device device() {
        return Device.builder().id(DEVICE_ID).displayName("test-board").audience(Audience.BOTH).build();
    }

    @Test
    void assembleForDayRunsProducersAtThatDaysMidnightWithoutWeather() {
        List<BoardContext> seen = new ArrayList<>();
        BoardPayloadAssembler assembler = new BoardPayloadAssembler(
                deviceRepository, weatherService, List.of(ctx -> {
                    seen.add(ctx);
                    return List.of();
                }, emitting("x")), DEFAULT_ZONE);

        MusallahBoardPayload payload = assembler.assembleForDay(device(), LocalDate.of(2026, 9, 24));

        assertThat(frameIdsOf(payload)).containsExactly("x");
        assertThat(payload.getWeather()).isNull();
        assertThat(seen).singleElement().satisfies(ctx -> {
            assertThat(ctx.isWholeDay()).isTrue();
            assertThat(ctx.getNow()).isEqualTo(ZonedDateTime.of(2026, 9, 24, 0, 0, 0, 0, DEFAULT_ZONE));
        });
        verifyNoInteractions(weatherService);
    }

    /**
     * A bundle carries its gaps for weeks with nobody watching, so the lenient per-producer
     * isolation of the live path does not apply.
     */
    @Test
    void assembleForDayPropagatesAProducerFailure() {
        BoardPayloadAssembler assembler = new BoardPayloadAssembler(
                deviceRepository, weatherService,
                List.of(emitting("a"), throwing(new IllegalStateException("poster query blew up"))),
                DEFAULT_ZONE);

        assertThatThrownBy(() -> assembler.assembleForDay(device(), LocalDate.of(2026, 9, 24)))
                .isInstanceOf(ApiResponseException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
