package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.service.board.producer.FrameProducer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link BoardPayloadAssembler#assembleForDay}: producers run in order against a
 * whole-day context, and a failing producer fails the day rather than shipping a gap.
 */
class BoardPayloadAssemblerTest {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Toronto");
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);

    private static Device device() {
        return Device.builder().id(DEVICE_ID).displayName("test-board").audience(Audience.BOTH).build();
    }

    private static BoardPayloadAssembler assemblerWith(FrameProducer... producers) {
        return new BoardPayloadAssembler(List.of(producers), DEFAULT_ZONE);
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
    void assembleForDayRunsProducersAtThatDaysMidnightWithoutWeather() {
        List<BoardContext> seen = new ArrayList<>();
        BoardPayloadAssembler assembler = assemblerWith(ctx -> {
            seen.add(ctx);
            return List.of();
        }, emitting("x"));

        MusallahBoardPayload payload = assembler.assembleForDay(device(), DAY);

        assertThat(frameIdsOf(payload)).containsExactly("x");
        assertThat(payload.getWeather()).isNull();
        assertThat(seen).singleElement().satisfies(ctx ->
                assertThat(ctx.getNow()).isEqualTo(ZonedDateTime.of(2026, 9, 24, 0, 0, 0, 0, DEFAULT_ZONE)));
    }

    /** Producers run in injected order. */
    @Test
    void framesKeepTheirProducerOrder() {
        BoardPayloadAssembler assembler = assemblerWith(emitting("a"), emitting("b"), emitting("c"));

        assertThat(frameIdsOf(assembler.assembleForDay(device(), DAY))).containsExactly("a", "b", "c");
    }

    /** A null return is a producer bug, not a reason to blank the day. */
    @Test
    void aProducerReturningNullIsTreatedAsEmpty() {
        BoardPayloadAssembler assembler = assemblerWith(ctx -> null, emitting("only"));

        assertThat(frameIdsOf(assembler.assembleForDay(device(), DAY))).containsExactly("only");
    }

    /**
     * A package carries its gaps for weeks with nobody watching, so a failing producer fails
     * the whole day.
     */
    @Test
    void assembleForDayPropagatesAProducerFailure() {
        BoardPayloadAssembler assembler = assemblerWith(
                emitting("a"), throwing(new IllegalStateException("poster query blew up")));

        assertThatThrownBy(() -> assembler.assembleForDay(device(), DAY))
                .isInstanceOf(ApiResponseException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** An {@link Error} is not wrapped as a producer failure. */
    @Test
    void errorsPropagateUnwrapped() {
        BoardPayloadAssembler assembler = assemblerWith(ctx -> {
            throw new StackOverflowError("recursion in a producer");
        });

        assertThatThrownBy(() -> assembler.assembleForDay(device(), DAY))
                .isInstanceOf(StackOverflowError.class);
    }
}
