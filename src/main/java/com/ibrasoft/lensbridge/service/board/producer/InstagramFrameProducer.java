package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.InstagramFrameConfig;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emits the INSTAGRAM frame. It carries no data — the QR destination is read client-side
 * from {@code deviceConfig.socialUrl}, which already has its own app-level fallback when
 * unset — but declaring it here gives it a real {@code durationInSeconds} and a place in the
 * ordered sequence instead of being synthesized and hardcoded in the frontend. Unlike the
 * other frame types, this one is not conditional on any backend data: it is always shown.
 */
@Component
@Order(7)
public class InstagramFrameProducer implements FrameProducer {

    @Override
    public List<FrameDefinition> produce(BoardContext ctx) {
        return List.of(FrameDefinition.builder()
                .frameId("instagram")
                .frameType(FrameType.INSTAGRAM)
                .durationInSeconds(13)
                .frameConfig(new InstagramFrameConfig())
                .build());
    }
}
