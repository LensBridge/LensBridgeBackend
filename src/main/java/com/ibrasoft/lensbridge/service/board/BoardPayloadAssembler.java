package com.ibrasoft.lensbridge.service.board;

import com.ibrasoft.lensbridge.dto.response.MusallahBoardPayload;
import com.ibrasoft.lensbridge.model.board.BoardConfig;
import com.ibrasoft.lensbridge.model.board.BoardLocation;
import com.ibrasoft.lensbridge.model.board.Event;
import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.board.transformer.EventListFrameTransformer;
import com.ibrasoft.lensbridge.service.board.transformer.PosterFrameTransformer;
import com.ibrasoft.lensbridge.service.board.transformer.WeeklyContentFrameTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardPayloadAssembler {

    private final BoardService boardService;
    private final PosterService posterService;
    private final PosterFrameTransformer posterTransformer;
    private final EventListFrameTransformer eventListTransformer;
    private final WeeklyContentFrameTransformer weeklyContentTransformer;

    public MusallahBoardPayload assemble(BoardLocation location) {
        BoardConfig config = boardService.getBoardConfig(location).orElse(null);
        BoardContext ctx = BoardContext.of(location, config);

        List<FrameDefinition> frames = new ArrayList<>();
        frames.addAll(posterFrames(ctx));
        frames.addAll(eventFrames(ctx));
        frames.addAll(weeklyContentFrames(ctx));

        return MusallahBoardPayload.builder()
                .boardConfig(config)
                .frames(frames)
                .build();
    }

    private List<FrameDefinition> posterFrames(BoardContext ctx) {
        List<Poster> posters = posterService.getActivePosterFramesForBoard(ctx.getLocation());
        List<FrameDefinition> out = new ArrayList<>(posters.size());
        for (Poster p : posters) out.add(posterTransformer.transform(p, ctx));
        return out;
    }

    private List<FrameDefinition> eventFrames(BoardContext ctx) {
        long weekStart = ctx.currentWeekStart().toEpochMilli();
        long weekEnd = ctx.currentWeekEnd().toEpochMilli();
        List<Event> events = boardService.getEventsForBoardInRange(ctx.getLocation(), weekStart, weekEnd);
        if (events.isEmpty()) return List.of();
        return List.of(eventListTransformer.transform(events, ctx));
    }

    private List<FrameDefinition> weeklyContentFrames(BoardContext ctx) {
        WeeklyContent content = boardService.getCurrentWeeklyContent().orElse(null);
        return weeklyContentTransformer.transform(content, ctx);
    }
}
