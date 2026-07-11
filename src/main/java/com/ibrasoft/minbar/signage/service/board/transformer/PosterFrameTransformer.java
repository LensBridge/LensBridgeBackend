package com.ibrasoft.minbar.signage.service.board.transformer;

import com.ibrasoft.minbar.signage.model.Poster;
import com.ibrasoft.minbar.signage.model.frames.FrameDefinition;
import com.ibrasoft.minbar.signage.model.frames.FrameSlot;
import com.ibrasoft.minbar.signage.model.frames.FrameType;
import com.ibrasoft.minbar.signage.model.frames.PosterFrameConfig;
import com.ibrasoft.minbar.signage.service.board.BoardContext;
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
