package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.service.board.producer.FrameProducer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the per-day payload files inside a board's signed content package. Boards are
 * local-first: they never fetch a live payload, they install these files and render them.
 */
@Service
@Slf4j
@AllArgsConstructor
public class BoardPayloadAssembler {

    /**
     * Every registered {@link FrameProducer}, Spring-ordered via {@code @Order} on each
     * implementation. 
     */
    private final List<FrameProducer> frameProducers;

    private final ZoneId defaultZone;

    /**
     * The payload for all of {@code day} in the device's timezone, with posters widened to the
     * whole day and no weather. Packages are built days ahead, so a forecast would be stale;
     * boards fetch current weather separately from {@code GET /api/agent/weather}.
     * <p>
     * A failing producer fails the whole call. A package carries the gap for weeks, and
     * nobody is watching the board to notice.
     */
    public MusallahBoardPayload assembleForDay(Device device, LocalDate day) {
        BoardContext ctx = BoardContext.of(device, defaultZone, day);

        return MusallahBoardPayload.builder()
                .deviceConfig(ctx.getConfig())
                .frames(produceFrames(ctx))
                .weather(null)
                .build();
    }

    /** The zone this assembler evaluates "today" in for {@code device}. */
    public ZoneId zoneFor(Device device) {
        return BoardContext.zoneFor(device, defaultZone);
    }

    private List<FrameDefinition> produceFrames(BoardContext ctx) {
        return frameProducers.stream()
                .flatMap(producer -> produceStrictly(producer, ctx).stream())
                .collect(Collectors.toList());
    }

    private static List<FrameDefinition> produceStrictly(FrameProducer producer, BoardContext ctx) {
        try {
            List<FrameDefinition> frames = producer.produce(ctx);
            return frames == null ? List.of() : frames;
        } catch (RuntimeException e) {
            log.error("Frame producer {} failed for device {} on {}",
                    producer.getClass().getSimpleName(), ctx.getDevice().getId(), ctx.today(), e);
            ApiResponseException failure = new ApiResponseException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorResponse.of("Could not assemble the payload for " + ctx.today() + ": "
                            + producer.getClass().getSimpleName() + " failed"),
                    e.getMessage());
            failure.initCause(e);
            throw failure;
        }
    }
}
