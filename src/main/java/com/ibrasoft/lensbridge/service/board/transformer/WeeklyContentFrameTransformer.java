package com.ibrasoft.lensbridge.service.board.transformer;

import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import com.ibrasoft.lensbridge.model.board.JummahPrayer;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameSlot;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.IslamicQuoteFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.JummahFrameConfig;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transforms a WeeklyContent document into up to three FrameDefinitions:
 * one for the verse, one for the hadith, and one for jummah prayer info.
 *
 * This transformer intentionally does NOT implement FrameTransformer<T> because
 * one source expands to multiple frames. The assembler (Phase 4) will inject it
 * by concrete class.
 */
@Component
public class WeeklyContentFrameTransformer {

    public List<FrameDefinition> transform(WeeklyContent content, BoardContext ctx) {
        List<FrameDefinition> out = new ArrayList<>();
        if (content == null) return out;

        if (content.getVerse() != null) {
            out.add(quoteFrame(content.getVerse(), IslamicQuoteFrameConfig.Kind.VERSE));
        }
        if (content.getHadith() != null) {
            out.add(quoteFrame(content.getHadith(), IslamicQuoteFrameConfig.Kind.HADITH));
        }
        if (content.getJummahPrayer() != null && !content.getJummahPrayer().isEmpty()) {
            out.add(jummahFrame(content.getJummahPrayer()));
        }
        return out;
    }

    private FrameDefinition quoteFrame(IslamicQuote quote, IslamicQuoteFrameConfig.Kind kind) {
        IslamicQuoteFrameConfig config = IslamicQuoteFrameConfig.builder()
                .kind(kind)
                .arabic(quote.getArabic())
                .transliteration(quote.getTransliteration())
                .translation(quote.getTranslation())
                .reference(quote.getReference())
                .build();

        return FrameDefinition.builder()
                .frameType(FrameType.ISLAMIC_QUOTE)
                .durationInSeconds(null)
                .frameConfig(config)
                .slot(FrameSlot.PRIMARY)
                .priority(null)
                .build();
    }

    private FrameDefinition jummahFrame(List<JummahPrayer> prayers) {
        List<JummahFrameConfig.JummahSlot> slots = prayers.stream()
                .map(p -> JummahFrameConfig.JummahSlot.builder()
                        .prayerTime(p.getPrayerTime() != null ? p.getPrayerTime().toString() : null)
                        .khatib(p.getKhatib())
                        .location(p.getLocation())
                        .build())
                .collect(Collectors.toList());

        JummahFrameConfig config = JummahFrameConfig.builder()
                .prayers(slots)
                .build();

        return FrameDefinition.builder()
                .frameType(FrameType.JUMMAH)
                .durationInSeconds(null)
                .frameConfig(config)
                .slot(FrameSlot.PRIMARY)
                .priority(null)
                .build();
    }
}
