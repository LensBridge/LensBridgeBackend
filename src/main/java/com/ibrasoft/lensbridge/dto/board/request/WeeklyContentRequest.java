package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyContentRequest {
    @Valid
    private List<QuoteEntry> quotes;
    private List<JummahSlot> jummahPrayers;

    public record QuoteEntry(
        IslamicQuote.Kind kind,
        String arabic,
        String transliteration,
        String translation,
        String reference,
        @Min(value = DeviceConfig.MIN_SLIDE_SECONDS,
             message = "durationSeconds must be null (auto) or between 5 and 120 seconds")
        @Max(value = DeviceConfig.MAX_SLIDE_SECONDS,
             message = "durationSeconds must be null (auto) or between 5 and 120 seconds")
        Integer durationSeconds) {}

    public record JummahSlot(
        String prayerTime,
        String khatib,
        String room) {}
}
