package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.minbar.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.minbar.board.frames.PromotableSocialMediaFrameConfig;
import com.ibrasoft.lensbridge.service.PromotableSocialMediaService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits one SOCIALS frame per social account promoted to this board's audience.
 * <p>
 * This replaced a single hardcoded Instagram frame whose QR destination was read client-side
 * from a since-removed {@code socialUrl} board-config field. Every field a social frame renders — platform, URL,
 * copy, duration — is now backend data, so a board can promote several accounts and an
 * operator can change any of them without a frontend deploy.
 */
@Component
@RequiredArgsConstructor
@Order(7)
public class SocialsFrameProducer implements FrameProducer {

    private final PromotableSocialMediaService socialMediaService;

    @Override
    public List<FrameDefinition> produce(BoardContext ctx) {
        List<PromotableSocialMedia> socials =
                socialMediaService.getForAudience(ctx.getDevice().getAudience());

        List<FrameDefinition> out = new ArrayList<>();

        for (PromotableSocialMedia social : socials) {
            PromotableSocialMediaFrameConfig config = PromotableSocialMediaFrameConfig.builder()
                    .url(social.getUrl())
                    .socialType(social.getType())
                    .headerText(social.getHeaderText())
                    .heroText(social.getHeroText())
                    .handle(social.getHandle())
                    .footerText(social.getFooterText())
                    .build();

            out.add(FrameDefinition.builder()
                    .frameId("social:" + social.getId())
                    .frameConfig(config)
                    .durationInSeconds(social.getDuration())
                    .frameType(FrameType.SOCIALS)
                    .build());
        }

        return out;
    }
}