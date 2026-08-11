package com.ibrasoft.lensbridge.config.converter;

import com.ibrasoft.lensbridge.model.board.SocialType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Binds {@code SocialType} path and query params. Spring's default enum binder matches on
 * {@code name()} only, so without this a request carrying the lowercase form the API itself
 * emits — {@code ?type=instagram} — would 400.
 */
@Component
public class StringToSocialTypeConverter implements Converter<String, SocialType> {
    @Override
    public SocialType convert(@NonNull String source) {
        return SocialType.from(source)
                .orElseThrow(() -> new IllegalArgumentException("Unknown social type: " + source));
    }
}
