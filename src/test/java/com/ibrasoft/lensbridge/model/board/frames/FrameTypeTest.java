package com.ibrasoft.lensbridge.model.board.frames;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameTypeTest {

    @Test
    void toStringReturnsSnakeCaseTypeName() {
        assertThat(FrameType.POSTER.toString()).isEqualTo("poster");
        assertThat(FrameType.EVENT_LIST.toString()).isEqualTo("event_list");
        assertThat(FrameType.DAILY_SCHEDULE.toString()).isEqualTo("daily_schedule");
        assertThat(FrameType.NEXT_PRAYER.toString()).isEqualTo("next_prayer");
        assertThat(FrameType.JUMMAH.toString()).isEqualTo("jummah");
        assertThat(FrameType.ISLAMIC_QUOTE.toString()).isEqualTo("islamic_quote");
    }

    @Test
    void toStringDiffersFromEnumNameForMultiWordTypes() {
        assertThat(FrameType.EVENT_LIST.toString()).isNotEqualTo(FrameType.EVENT_LIST.name());
    }
}
