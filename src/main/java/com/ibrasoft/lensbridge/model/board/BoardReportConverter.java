package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a {@link BoardReport} as JSON text, so the column works on Postgres and SQLite alike. */
@Converter
public class BoardReportConverter implements AttributeConverter<BoardReport, String> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(BoardReport report) {
        if (report == null) return null;
        try {
            return JSON.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Board report cannot be stored", e);
        }
    }

    @Override
    public BoardReport convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, BoardReport.class);
        } catch (JsonProcessingException e) {
            // A report this backend cannot read is as good as none; the next heartbeat replaces it.
            return null;
        }
    }
}
