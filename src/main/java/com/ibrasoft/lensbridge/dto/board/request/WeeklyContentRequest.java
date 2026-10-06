package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyContentRequest {
    @Valid
    private List<QuoteEntry> quotes;
    @Valid
    private List<JummahSlot> jummahPrayers;

    public record QuoteEntry(
        IslamicQuote.Kind kind,
        @Size(max = IslamicQuote.QUOTE_TEXT_MAX, message = "arabic must be at most 4000 characters")
        String arabic,
        @Size(max = IslamicQuote.QUOTE_TEXT_MAX, message = "transliteration must be at most 4000 characters")
        String transliteration,
        @Size(max = IslamicQuote.QUOTE_TEXT_MAX, message = "translation must be at most 4000 characters")
        String translation,
        @Size(max = IslamicQuote.REFERENCE_MAX, message = "reference must be at most 1000 characters")
        String reference,
        @Min(value = DeviceConfig.MIN_SLIDE_SECONDS,
             message = "durationSeconds must be null (auto) or between 5 and 120 seconds")
        @Max(value = DeviceConfig.MAX_SLIDE_SECONDS,
             message = "durationSeconds must be null (auto) or between 5 and 120 seconds")
        Integer durationSeconds) {}

    public record JummahSlot(
        // 24-hour HH:mm, e.g. 13:30. Checked in BoardService rather than with @Pattern, which
        // would put a regex into the generated schema for what is a parse error.
        String prayerTime,
        @Size(max = 255, message = "khatib must be at most 255 characters")
        String khatib,
        @Size(max = 255, message = "room must be at most 255 characters")
        String room) {}
}
