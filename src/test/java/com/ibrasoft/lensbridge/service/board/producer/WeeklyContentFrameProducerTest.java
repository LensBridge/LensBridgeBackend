package com.ibrasoft.lensbridge.service.board.producer;

import com.ibrasoft.lensbridge.model.board.IslamicQuote;
import com.ibrasoft.lensbridge.model.board.JummahPrayer;
import com.ibrasoft.lensbridge.model.board.WeeklyContent;
import com.ibrasoft.lensbridge.model.board.frames.FrameDefinition;
import com.ibrasoft.lensbridge.model.board.frames.FrameType;
import com.ibrasoft.lensbridge.model.board.frames.IslamicQuoteFrameConfig;
import com.ibrasoft.lensbridge.model.board.frames.JummahFrameConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WeeklyContentFrameProducerTest {

    @InjectMocks
    private WeeklyContentFrameProducer transformer;

    private IslamicQuote quote(IslamicQuote.Kind kind, String ref) {
        return IslamicQuote.builder()
                .id(UUID.randomUUID())
                .kind(kind)
                .arabic("arabic-" + ref)
                .transliteration("translit-" + ref)
                .translation("translation-" + ref)
                .reference(ref)
                .build();
    }

    private JummahPrayer prayer(LocalTime time, String khatib, String room) {
        return JummahPrayer.builder()
                .prayerTime(time)
                .khatib(khatib)
                .room(room)
                .build();
    }

    @Test
    void nullContentReturnsEmptyList() {
        assertThat(transformer.transform(null, null)).isEmpty();
    }

    @Test
    void emptyContentReturnsEmptyList() {
        WeeklyContent content = WeeklyContent.builder().build();

        assertThat(transformer.transform(content, null)).isEmpty();
    }

    @Test
    void verseQuoteMapsToVerseKindFrame() {
        IslamicQuote q = quote(IslamicQuote.Kind.VERSE, "2:255");
        WeeklyContent content = WeeklyContent.builder()
                .quotes(List.of(q))
                .build();

        List<FrameDefinition> frames = transformer.transform(content, null);

        assertThat(frames).hasSize(1);
        FrameDefinition def = frames.get(0);
        assertThat(def.getFrameId()).isEqualTo("quote:" + q.getId());
        assertThat(def.getFrameType()).isEqualTo(FrameType.ISLAMIC_QUOTE);
        assertThat(def.getDurationInSeconds()).isNull();

        IslamicQuoteFrameConfig config = (IslamicQuoteFrameConfig) def.getFrameConfig();
        assertThat(config.getKind()).isEqualTo(IslamicQuoteFrameConfig.Kind.VERSE);
        assertThat(config.getArabic()).isEqualTo("arabic-2:255");
        assertThat(config.getTransliteration()).isEqualTo("translit-2:255");
        assertThat(config.getTranslation()).isEqualTo("translation-2:255");
        assertThat(config.getReference()).isEqualTo("2:255");
    }

    @Test
    void hadithQuoteMapsToHadithKindFrame() {
        WeeklyContent content = WeeklyContent.builder()
                .quotes(List.of(quote(IslamicQuote.Kind.HADITH, "Bukhari 1")))
                .build();

        List<FrameDefinition> frames = transformer.transform(content, null);

        IslamicQuoteFrameConfig config = (IslamicQuoteFrameConfig) frames.get(0).getFrameConfig();
        assertThat(config.getKind()).isEqualTo(IslamicQuoteFrameConfig.Kind.HADITH);
    }

    @Test
    void nullKindQuoteFallsBackToHadith() {
        WeeklyContent content = WeeklyContent.builder()
                .quotes(List.of(quote(null, "unknown")))
                .build();

        List<FrameDefinition> frames = transformer.transform(content, null);

        IslamicQuoteFrameConfig config = (IslamicQuoteFrameConfig) frames.get(0).getFrameConfig();
        assertThat(config.getKind()).isEqualTo(IslamicQuoteFrameConfig.Kind.HADITH);
    }

    @Test
    void multipleQuotesProduceOneFramePerQuoteInOrder() {
        WeeklyContent content = WeeklyContent.builder()
                .quotes(List.of(
                        quote(IslamicQuote.Kind.VERSE, "Q1"),
                        quote(IslamicQuote.Kind.HADITH, "Q2")))
                .build();

        List<FrameDefinition> frames = transformer.transform(content, null);

        assertThat(frames).hasSize(2);
        assertThat(((IslamicQuoteFrameConfig) frames.get(0).getFrameConfig()).getReference()).isEqualTo("Q1");
        assertThat(((IslamicQuoteFrameConfig) frames.get(1).getFrameConfig()).getReference()).isEqualTo("Q2");
    }

    @Test
    void jummahPrayersProduceSingleJummahFrameWithSlots() {
        WeeklyContent content = WeeklyContent.builder()
                .jummahPrayers(List.of(
                        prayer(LocalTime.of(13, 30), "Imam A", "Hall 1"),
                        prayer(LocalTime.of(14, 30), "Imam B", "Hall 2")))
                .build();

        List<FrameDefinition> frames = transformer.transform(content, null);

        assertThat(frames).hasSize(1);
        FrameDefinition def = frames.get(0);
        assertThat(def.getFrameId()).isEqualTo("jummah");
        assertThat(def.getFrameType()).isEqualTo(FrameType.JUMMAH);

        JummahFrameConfig config = (JummahFrameConfig) def.getFrameConfig();
        assertThat(config.getPrayers()).hasSize(2);
        JummahFrameConfig.JummahSlot first = config.getPrayers().get(0);
        assertThat(first.getPrayerTime()).isEqualTo("13:30");
        assertThat(first.getKhatib()).isEqualTo("Imam A");
        assertThat(first.getRoom()).isEqualTo("Hall 1");
        assertThat(config.getPrayers().get(1).getPrayerTime()).isEqualTo("14:30");
    }

    @Test
    void jummahSlotWithNullPrayerTimeYieldsNullStringTime() {
        WeeklyContent content = WeeklyContent.builder()
                .jummahPrayers(List.of(prayer(null, "Imam", "Room")))
                .build();

        List<FrameDefinition> frames = transformer.transform(content, null);

        JummahFrameConfig config = (JummahFrameConfig) frames.get(0).getFrameConfig();
        assertThat(config.getPrayers().get(0).getPrayerTime()).isNull();
    }

    @Test
    void quotesAndJummahProduceQuoteFramesThenJummahFrame() {
        List<JummahPrayer> prayers = new ArrayList<>();
        prayers.add(prayer(LocalTime.of(13, 0), "K", "R"));
        WeeklyContent content = WeeklyContent.builder()
                .quotes(List.of(quote(IslamicQuote.Kind.VERSE, "V")))
                .jummahPrayers(prayers)
                .build();

        List<FrameDefinition> frames = transformer.transform(content, null);

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).getFrameType()).isEqualTo(FrameType.ISLAMIC_QUOTE);
        assertThat(frames.get(1).getFrameType()).isEqualTo(FrameType.JUMMAH);
    }
}
