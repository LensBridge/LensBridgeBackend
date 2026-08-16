package com.ibrasoft.lensbridge.model.board.frames;

import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameTypeTest {

    @Test
    void toStringReturnsSnakeCaseTypeName() {
        assertThat(FrameType.POSTER.toString()).isEqualTo("poster");
        assertThat(FrameType.AGENDA.toString()).isEqualTo("agenda");
        assertThat(FrameType.NEXT_PRAYER.toString()).isEqualTo("next_prayer");
        assertThat(FrameType.JUMMAH.toString()).isEqualTo("jummah");
        assertThat(FrameType.ISLAMIC_QUOTE.toString()).isEqualTo("islamic_quote");
    }

    @Test
    void toStringDiffersFromEnumNameForMultiWordTypes() {
        assertThat(FrameType.NEXT_PRAYER.toString()).isNotEqualTo(FrameType.NEXT_PRAYER.name());
    }
}
