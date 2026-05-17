package com.ibrasoft.lensbridge.config.converter;

import com.ibrasoft.lensbridge.model.board.Audience;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringToAudienceConverterTest {

    private final StringToAudienceConverter converter = new StringToAudienceConverter();

    @Test
    void convertsLowercaseDisplayName() {
        assertThat(converter.convert("brothers")).isEqualTo(Audience.BROTHERS);
        assertThat(converter.convert("sisters")).isEqualTo(Audience.SISTERS);
        assertThat(converter.convert("both")).isEqualTo(Audience.BOTH);
    }

    @Test
    void convertsEnumName() {
        assertThat(converter.convert("BROTHERS")).isEqualTo(Audience.BROTHERS);
    }

    @Test
    void convertsIsCaseInsensitive() {
        assertThat(converter.convert("BrOtHeRs")).isEqualTo(Audience.BROTHERS);
        assertThat(converter.convert("Both")).isEqualTo(Audience.BOTH);
    }

    @Test
    void convertThrowsForUnknownAudience() {
        assertThatThrownBy(() -> converter.convert("everyone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown audience: everyone");
    }

    @Test
    void convertThrowsForEmptyString() {
        assertThatThrownBy(() -> converter.convert(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
