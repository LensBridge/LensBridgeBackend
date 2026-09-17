package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.PosterFrameConfig;
import com.ibrasoft.lensbridge.service.PosterService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Order(3)
public class PosterFrameProducer implements FrameProducer {

    private static final String POSTER_FRAME_ID = "poster:";

    private final PosterService posterService;

    @Override
    public List<FrameDefinition> produce(BoardContext ctx) {
        List<Poster> posters =
                posterService.getActivePosterFramesForAudience(ctx.getDevice().getAudience());
        return posters.stream().map(p -> transform(p, ctx)).collect(Collectors.toList());
    }

    public FrameDefinition transform(Poster poster, BoardContext ctx) {
        PosterFrameConfig config = PosterFrameConfig.builder()
                .posterUrl(poster.getImage())
                .title(poster.getTitle())
                .signupUrl(poster.getSignupUrl())
                .build();

        return FrameDefinition.builder()
                .frameId(POSTER_FRAME_ID + poster.getId())
                .frameType(FrameType.POSTER)
                .durationInSeconds(poster.getDuration())
                .frameConfig(config)
                .build();
    }
}
