package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.OpenWeatherService;
import com.ibrasoft.lensbridge.service.board.producer.FrameProducer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class BoardPayloadAssembler {

    private final DeviceRepository deviceRepository;
    private final OpenWeatherService openWeatherService;

    /**
     * Every registered {@link FrameProducer}, Spring-ordered via {@code @Order} on each
     * implementation. 
     */
    private final List<FrameProducer> frameProducers;

    private final ZoneId defaultZone;

    public MusallahBoardPayload assemble(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Device not found: " + deviceId)));

        BoardContext ctx = BoardContext.of(device, defaultZone);

        return MusallahBoardPayload.builder()
                .deviceConfig(ctx.getConfig())
                .frames(produceFrames(ctx, true))
                .weather(openWeatherService.getCurrentWeather())
                .build();
    }

    /**
     * The payload {@link #assemble} would return at the start of {@code day} in the device's
     * timezone, with posters widened to the whole day and no weather. Used for offline
     * bundles, which are built days ahead — a forecast would be stale, and fetching one per
     * day would burn the OpenWeather quota for nothing.
     * <p>
     * Unlike the live path, a failing producer fails the whole call. A live board that loses
     * a producer's slides gets them back on its next poll; a bundle carries the gap for
     * weeks, and nobody is watching the board to notice.
     */
    public MusallahBoardPayload assembleForDay(Device device, LocalDate day) {
        BoardContext ctx = BoardContext.of(device, defaultZone, day);

        return MusallahBoardPayload.builder()
                .deviceConfig(ctx.getConfig())
                .frames(produceFrames(ctx, false))
                .weather(null)
                .build();
    }

    /** The zone this assembler evaluates "today" in for {@code device}. */
    public ZoneId zoneFor(Device device) {
        return BoardContext.zoneFor(device, defaultZone);
    }

    private List<FrameDefinition> produceFrames(BoardContext ctx, boolean tolerateFailures) {
        return frameProducers.stream()
                .flatMap(producer -> (tolerateFailures
                        ? produceSafely(producer, ctx)
                        : produceStrictly(producer, ctx)).stream())
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

    /**
     * Runs one producer, absorbing anything it throws so the rest of the payload still ships.
     * <p>
     * These boards are unattended wall displays. A producer whose query fails should cost the
     * board that producer's slides. A single producer failing should not make the entire API
     * 500
     */
    private List<FrameDefinition> produceSafely(FrameProducer producer, BoardContext ctx) {
        try {
            List<FrameDefinition> frames = producer.produce(ctx);
            return frames == null ? List.of() : frames;
        } catch (RuntimeException e) {
            log.error("Frame producer {} failed for device {}; omitting its frames from this payload",
                    producer.getClass().getSimpleName(), ctx.getDevice().getId(), e);
            return List.of();
        }
    }
}
