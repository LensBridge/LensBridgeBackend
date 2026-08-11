package com.ibrasoft.lensbridge.config.converter;

import com.ibrasoft.lensbridge.model.board.SocialType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringToSocialTypeConverterTest {

    private final StringToSocialTypeConverter converter = new StringToSocialTypeConverter();

    @Test
    void convertsLowercaseLabel() {
        assertThat(converter.convert("instagram")).isEqualTo(SocialType.INSTAGRAM);
        assertThat(converter.convert("whatsapp")).isEqualTo(SocialType.WHATSAPP);
        assertThat(converter.convert("other")).isEqualTo(SocialType.OTHER);
    }

    @Test
    void convertsEnumName() {
        assertThat(converter.convert("TIKTOK")).isEqualTo(SocialType.TIKTOK);
    }

    @Test
    void convertIsCaseInsensitive() {
        assertThat(converter.convert("YouTube")).isEqualTo(SocialType.YOUTUBE);
    }

    @Test
    void convertThrowsForUnknownType() {
        assertThatThrownBy(() -> converter.convert("myspace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown social type: myspace");
    }

    @Test
    void convertThrowsForEmptyString() {
        assertThatThrownBy(() -> converter.convert(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
