package com.ibrasoft.lensbridge.model.board;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SocialTypeTest {

    @Test
    void toStringReturnsLowercaseLabel() {
        assertThat(SocialType.INSTAGRAM.toString()).isEqualTo("instagram");
        assertThat(SocialType.YOUTUBE.toString()).isEqualTo("youtube");
        assertThat(SocialType.TIKTOK.toString()).isEqualTo("tiktok");
        assertThat(SocialType.WHATSAPP.toString()).isEqualTo("whatsapp");
        assertThat(SocialType.OTHER.toString()).isEqualTo("other");
    }

    @Test
    void toStringDiffersFromEnumName() {
        assertThat(SocialType.INSTAGRAM.toString()).isNotEqualTo(SocialType.INSTAGRAM.name());
    }

    @Test
    void fromAcceptsEitherCase() {
        assertThat(SocialType.from("instagram")).contains(SocialType.INSTAGRAM);
        assertThat(SocialType.from("INSTAGRAM")).contains(SocialType.INSTAGRAM);
        assertThat(SocialType.from("InStAgRaM")).contains(SocialType.INSTAGRAM);
    }

    @Test
    void fromReturnsEmptyForNullAndUnknown() {
        assertThat(SocialType.from(null)).isEmpty();
        assertThat(SocialType.from("myspace")).isEmpty();
    }

    /** Pinned against the enum so a new platform cannot skip a label. */
    @Test
    void everyValueRoundTripsThroughItsLabel() {
        for (SocialType type : SocialType.values()) {
            assertThat(SocialType.from(type.toString())).isEqualTo(Optional.of(type));
        }
    }
}