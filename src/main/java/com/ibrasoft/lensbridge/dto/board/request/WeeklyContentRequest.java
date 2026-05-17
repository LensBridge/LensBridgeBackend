package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyContentRequest {
    private List<QuoteEntry> quotes;
    private List<JummahSlot> jummahPrayers;

    public record QuoteEntry(
        IslamicQuote.Kind kind,
        String arabic,
        String transliteration,
        String translation,
        String reference) {}

    public record JummahSlot(
        String prayerTime,
        String khatib,
        String room) {}
}
