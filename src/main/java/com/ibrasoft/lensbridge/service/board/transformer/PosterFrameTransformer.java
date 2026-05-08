package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.model.board.Poster;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameSlot;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.PosterFrameConfig;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.springframework.stereotype.Component;

@Component
public class PosterFrameTransformer implements FrameTransformer<Poster> {

    @Override
    public FrameType supports() {
        return FrameType.POSTER;
    }

    @Override
    public FrameDefinition transform(Poster poster, BoardContext ctx) {
        PosterFrameConfig config = PosterFrameConfig.builder()
                .posterUrl(poster.getImage())
                .title(poster.getTitle())
                .build();

        return FrameDefinition.builder()
                .frameType(FrameType.POSTER)
                .durationInSeconds(poster.getDuration())
                .frameConfig(config)
                .slot(FrameSlot.PRIMARY)
                .priority(null)
                .build();
    }
}
