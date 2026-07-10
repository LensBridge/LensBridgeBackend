package com.ibrasoft.minbar.config.converter;

import com.ibrasoft.minbar.model.board.Audience;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToAudienceConverter implements Converter<String, Audience> {
    @Override
    public Audience convert(@NonNull String source) {
        return Audience.from(source)
                .orElseThrow(() -> new IllegalArgumentException("Unknown audience: " + source));
    }
}
