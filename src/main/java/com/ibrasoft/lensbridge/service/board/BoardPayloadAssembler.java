package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.dto.board.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.BoardEvent;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.OpenWeatherService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.board.transformer.DailyScheduleFrameTransformer;
import com.ibrasoft.lensbridge.service.board.transformer.EventListFrameTransformer;
import com.ibrasoft.lensbridge.service.board.transformer.PosterFrameTransformer;
import com.ibrasoft.lensbridge.service.board.transformer.WeeklyContentFrameTransformer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class BoardPayloadAssembler {

    private final BoardService boardService;
    private final PosterService posterService;
    private final DeviceRepository deviceRepository;
    private final PosterFrameTransformer posterTransformer;
    private final EventListFrameTransformer eventListTransformer;
    private final DailyScheduleFrameTransformer dailyScheduleTransformer;
    private final WeeklyContentFrameTransformer weeklyContentTransformer;
    private final OpenWeatherService openWeatherService;

    private final ZoneId defaultZone;

    public MusallahBoardPayload assemble(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Device not found: " + deviceId)));

        BoardContext ctx = BoardContext.of(device, defaultZone);

        List<FrameDefinition> frames = new ArrayList<>();
        frames.addAll(posterFrames(ctx));
        frames.addAll(dailyScheduleFrames(ctx));
        frames.addAll(eventFrames(ctx));
        frames.addAll(weeklyContentFrames(ctx));

        return MusallahBoardPayload.builder()
                .deviceConfig(ctx.getConfig())
                .frames(frames)
                .weather(openWeatherService.getCurrentWeather())
                .build();
    }

    private List<FrameDefinition> posterFrames(BoardContext ctx) {
        List<Poster> posters = posterService.getActivePosterFramesForAudience(ctx.getDevice().getAudience());
        List<FrameDefinition> out = new ArrayList<>(posters.size());
        for (Poster p : posters) out.add(posterTransformer.transform(p, ctx));
        return out;
    }

    private List<FrameDefinition> dailyScheduleFrames(BoardContext ctx) {
        List<BoardEvent> todaysEvents = boardService.getEventsForAudienceInRange(
                ctx.getDevice().getAudience(), ctx.currentDayStart(), ctx.currentDayEnd());
        if (todaysEvents.isEmpty()) return List.of();
        return List.of(dailyScheduleTransformer.transform(todaysEvents, ctx));
    }

    private List<FrameDefinition> eventFrames(BoardContext ctx) {
        List<BoardEvent> boardEvents = boardService.getEventsForAudienceInRange(
                ctx.getDevice().getAudience(), ctx.currentWeekStart(), ctx.currentWeekEnd());
        if (boardEvents.isEmpty()) return List.of();
        return List.of(eventListTransformer.transform(boardEvents, ctx));
    }

    private List<FrameDefinition> weeklyContentFrames(BoardContext ctx) {
        WeeklyContent content = boardService.getWeeklyContentFor(ctx.today()).orElse(null);
        return weeklyContentTransformer.transform(content, ctx);
    }
}
