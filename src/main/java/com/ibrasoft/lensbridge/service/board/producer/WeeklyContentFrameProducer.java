package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import com.ibrasoft.lensbridge.model.board.JummahPrayer;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.IslamicQuoteFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.JummahFrameConfig;
import com.ibrasoft.lensbridge.service.BoardService;
import com.ibrasoft.lensbridge.service.board.BoardContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transforms a WeeklyContent entity into FrameDefinitions:
 * one per quote (VERSE/HADITH kind) and one for jummah prayer info.
 *
 * This intentionally does NOT implement FrameTransformer<T> because one source expands
 * to multiple frames — but it does implement {@link FrameProducer}, since that interface
 * already returns a List<FrameDefinition>.
 */
@Component
@RequiredArgsConstructor
@Order(6)
public class WeeklyContentFrameProducer implements FrameProducer {

    private final BoardService boardService;

    private final static String JUMMAH_FRAME_ID = "jummah";
    private final static String QUOTE_FRAME_ID = "quote:";

    @Override
    public List<FrameDefinition> produce(BoardContext ctx) {
        WeeklyContent content = boardService.getWeeklyContentFor(ctx.today()).orElse(null);
        return transform(content, ctx);
    }

    public List<FrameDefinition> transform(WeeklyContent content, BoardContext ctx) {
        List<FrameDefinition> out = new ArrayList<>();
        if (content == null) return out;

        for (IslamicQuote quote : content.getQuotes()) {
            IslamicQuoteFrameConfig.Kind kind = quote.getKind() == IslamicQuote.Kind.VERSE
                    ? IslamicQuoteFrameConfig.Kind.VERSE
                    : IslamicQuoteFrameConfig.Kind.HADITH;
            out.add(quoteFrame(quote, kind));
        }

        if (!content.getJummahPrayers().isEmpty()) {
            out.add(jummahFrame(content.getJummahPrayers()));
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
                .frameId(QUOTE_FRAME_ID + quote.getId())
                .frameType(FrameType.ISLAMIC_QUOTE)
                .durationInSeconds(quote.getDurationSeconds())
                .frameConfig(config)
                .build();
    }

    private FrameDefinition jummahFrame(List<JummahPrayer> prayers) {
        List<JummahFrameConfig.JummahSlot> slots = prayers.stream()
                .map(p -> JummahFrameConfig.JummahSlot.builder()
                        .prayerTime(p.getPrayerTime() != null ? p.getPrayerTime().toString() : null)
                        .khatib(p.getKhatib())
                        .room(p.getRoom())
                        .build())
                .collect(Collectors.toList());

        JummahFrameConfig config = JummahFrameConfig.builder()
                .prayers(slots)
                .build();

        return FrameDefinition.builder()
                .frameId(JUMMAH_FRAME_ID)
                .frameType(FrameType.JUMMAH)
                .durationInSeconds(null)
                .frameConfig(config)
                .build();
    }
}
