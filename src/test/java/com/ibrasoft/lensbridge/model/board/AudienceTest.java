package com.ibrasoft.lensbridge.model.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceTest {

    @Test
    void toStringReturnsDisplayName() {
        assertThat(Audience.BROTHERS.toString()).isEqualTo("brothers");
        assertThat(Audience.SISTERS.toString()).isEqualTo("sisters");
        assertThat(Audience.BOTH.toString()).isEqualTo("both");
    }

    @Test
    void fromMatchesEnumNameCaseInsensitively() {
        assertThat(Audience.from("BROTHERS")).contains(Audience.BROTHERS);
        assertThat(Audience.from("brothers")).contains(Audience.BROTHERS);
    }

    @Test
    void fromMatchesDisplayNameCaseInsensitively() {
        assertThat(Audience.from("Sisters")).contains(Audience.SISTERS);
        assertThat(Audience.from("BOTH")).contains(Audience.BOTH);
    }

    @Test
    void fromReturnsEmptyForNull() {
        assertThat(Audience.from(null)).isEmpty();
    }

    @Test
    void fromReturnsEmptyForUnknownValue() {
        assertThat(Audience.from("everyone")).isEmpty();
    }

    @Test
    void fromReturnsEmptyForBlankString() {
        assertThat(Audience.from("   ")).isEmpty();
    }
}
