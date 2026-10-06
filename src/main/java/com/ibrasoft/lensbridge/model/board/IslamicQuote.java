package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "islamic_quotes")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IslamicQuote {
    public enum Kind { VERSE, HADITH }

    /** Longest arabic, transliteration or translation text. Roomy enough for a full hadith. */
    public static final int QUOTE_TEXT_MAX = 4000;
    public static final int REFERENCE_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_content_id", nullable = false)
    @JsonIgnore
    private WeeklyContent weeklyContent;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    // Lengths match V4__widen_board_text_columns and the @Size on WeeklyContentRequest.QuoteEntry.
    @Column(length = QUOTE_TEXT_MAX)
    private String arabic;
    @Column(length = QUOTE_TEXT_MAX)
    private String transliteration;
    @Column(length = QUOTE_TEXT_MAX)
    private String translation;
    @Column(length = REFERENCE_MAX)
    private String reference;

    /**
     * How long this quote's slide is pinned for, or null for "auto" — the board then sizes the
     * dwell time to the text it is actually rendering.
     * <p>
     * Per-quote rather than per-device, like {@link com.ibrasoft.lensbridge.model.board.Poster#getDuration()}:
     * a two-line verse and a full-paragraph hadith do not deserve the same dwell time, and that
     * difference belongs to the content, not to the screen showing it. Every board renders the
     * same weekly content, so a device-level setting could not express it.
     * <p>
     * Nullable because "auto" has to remain reachable — a hadith nobody has timed by hand is
     * better measured by the board than pinned to a guessed constant.
     */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
}
