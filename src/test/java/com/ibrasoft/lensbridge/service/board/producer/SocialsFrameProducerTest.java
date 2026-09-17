package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.PromotableSocialMedia;
import com.ibrasoft.lensbridge.model.board.SocialType;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.PromotableSocialMediaFrameConfig;
import com.ibrasoft.lensbridge.service.PromotableSocialMediaService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialsFrameProducerTest {

    @Mock
    private PromotableSocialMediaService socialMediaService;

    @InjectMocks
    private SocialsFrameProducer producer;

    private BoardContext ctx(Audience audience) {
        Device device = Device.builder()
                .id(UUID.randomUUID())
                .displayName("Musallah A")
                .audience(audience)
                .build();
        return BoardContext.builder()
                .device(device)
                .now(ZonedDateTime.now(ZoneId.of("America/Toronto")))
                .build();
    }

    private PromotableSocialMedia social(SocialType type, String url, int duration) {
        return PromotableSocialMedia.builder()
                .id(UUID.randomUUID())
                .name(type + " page")
                .duration(duration)
                .audience(Audience.BOTH)
                .type(type)
                .url(url)
                .headerText("follow along")
                .heroText("your home on campus")
                .handle("@utmmsa")
                .footerText("Event recaps, announcements, and more!")
                .build();
    }

    @Test
    void producesOneFrameEachMappingEveryConfigField() {
        PromotableSocialMedia ig = social(SocialType.INSTAGRAM, "https://instagram.com/utmmsa", 13);
        when(socialMediaService.getForAudience(Audience.SISTERS)).thenReturn(List.of(ig));

        List<FrameDefinition> frames = producer.produce(ctx(Audience.SISTERS));

        assertThat(frames).hasSize(1);
        FrameDefinition def = frames.get(0);
        assertThat(def.getFrameId()).isEqualTo("social:" + ig.getId());
        assertThat(def.getFrameType()).isEqualTo(FrameType.SOCIALS);
        assertThat(def.getDurationInSeconds()).isEqualTo(13);
        assertThat(def.getFrameConfig()).isInstanceOf(PromotableSocialMediaFrameConfig.class);

        PromotableSocialMediaFrameConfig config = (PromotableSocialMediaFrameConfig) def.getFrameConfig();
        assertThat(config.getSocialType()).isEqualTo(SocialType.INSTAGRAM);
        assertThat(config.getUrl()).isEqualTo("https://instagram.com/utmmsa");
        assertThat(config.getHeaderText()).isEqualTo("follow along");
        assertThat(config.getHeroText()).isEqualTo("your home on campus");
        assertThat(config.getHandle()).isEqualTo("@utmmsa");
        assertThat(config.getFooterText()).isEqualTo("Event recaps, announcements, and more!");
    }

    @Test
    void producesAFrameForEverySocialInRepositoryOrder() {
        PromotableSocialMedia ig = social(SocialType.INSTAGRAM, "https://instagram.com/utmmsa", 13);
        PromotableSocialMedia wa = social(SocialType.WHATSAPP, "https://chat.whatsapp.com/x", 9);
        when(socialMediaService.getForAudience(Audience.BROTHERS)).thenReturn(List.of(ig, wa));

        List<FrameDefinition> frames = producer.produce(ctx(Audience.BROTHERS));

        assertThat(frames).extracting(FrameDefinition::getFrameId)
                .containsExactly("social:" + ig.getId(), "social:" + wa.getId());
        assertThat(frames).extracting(FrameDefinition::getDurationInSeconds)
                .containsExactly(13, 9);
    }

    @Test
    void queriesUsingTheDevicesOwnAudience() {
        when(socialMediaService.getForAudience(Audience.SISTERS)).thenReturn(List.of());

        producer.produce(ctx(Audience.SISTERS));

        verify(socialMediaService).getForAudience(Audience.SISTERS);
    }

    @Test
    void producesNothingWhenNoSocialsArePromoted() {
        when(socialMediaService.getForAudience(Audience.BOTH)).thenReturn(List.of());

        assertThat(producer.produce(ctx(Audience.BOTH))).isEmpty();
    }
}
