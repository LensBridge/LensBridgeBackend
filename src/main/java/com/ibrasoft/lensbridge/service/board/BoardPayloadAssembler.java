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

        List<FrameDefinition> frames = frameProducers.stream()
                .flatMap(producer -> producer.produce(ctx).stream())
                .collect(Collectors.toList());

        return MusallahBoardPayload.builder()
                .deviceConfig(ctx.getConfig())
                .frames(frames)
                .weather(openWeatherService.getCurrentWeather())
                .build();
    }
}
