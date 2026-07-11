package com.ibrasoft.minbar.signage.service.board;

import com.ibrasoft.minbar.media.dto.response.ErrorResponse;
import com.ibrasoft.minbar.signage.dto.response.MusallahBoardPayload;
import com.ibrasoft.minbar.shared.exception.ApiResponseException;
import com.ibrasoft.minbar.signage.model.Device;
import com.ibrasoft.minbar.signage.model.BoardEvent;
import com.ibrasoft.minbar.signage.model.Poster;
import com.ibrasoft.minbar.signage.model.WeeklyContent;
import com.ibrasoft.minbar.signage.model.frames.FrameDefinition;
import com.ibrasoft.minbar.signage.repository.DeviceRepository;
import com.ibrasoft.minbar.signage.service.BoardService;
import com.ibrasoft.minbar.signage.service.OpenWeatherService;
import com.ibrasoft.minbar.signage.service.PosterService;
import com.ibrasoft.minbar.signage.service.board.transformer.EventListFrameTransformer;
import com.ibrasoft.minbar.signage.service.board.transformer.PosterFrameTransformer;
import com.ibrasoft.minbar.signage.service.board.transformer.WeeklyContentFrameTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardPayloadAssembler {

    private final BoardService boardService;
    private final PosterService posterService;
    private final DeviceRepository deviceRepository;
    private final PosterFrameTransformer posterTransformer;
    private final EventListFrameTransformer eventListTransformer;
    private final WeeklyContentFrameTransformer weeklyContentTransformer;
    private final OpenWeatherService openWeatherService;

    public MusallahBoardPayload assemble(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Device not found: " + deviceId)));

        BoardContext ctx = BoardContext.of(device);

        List<FrameDefinition> frames = new ArrayList<>();
        frames.addAll(posterFrames(ctx));
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

    private List<FrameDefinition> eventFrames(BoardContext ctx) {
        List<BoardEvent> boardEvents = boardService.getEventsForAudienceInRange(
                ctx.getDevice().getAudience(), ctx.currentWeekStart(), ctx.currentWeekEnd());
        if (boardEvents.isEmpty()) return List.of();
        return List.of(eventListTransformer.transform(boardEvents, ctx));
    }

    private List<FrameDefinition> weeklyContentFrames(BoardContext ctx) {
        WeeklyContent content = boardService.getCurrentWeeklyContent().orElse(null);
        return weeklyContentTransformer.transform(content, ctx);
    }
}
