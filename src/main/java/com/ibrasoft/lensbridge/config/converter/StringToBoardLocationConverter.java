package com.ibrasoft.lensbridge.config.converter;

import com.ibrasoft.lensbridge.model.board.BoardLocation;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToBoardLocationConverter implements Converter<String, BoardLocation> {
    @Override
    public BoardLocation convert(@NonNull String source) {
        return BoardLocation.from(source)
                .orElseThrow(() -> new IllegalArgumentException("Unknown board location: " + source));
    }
}
