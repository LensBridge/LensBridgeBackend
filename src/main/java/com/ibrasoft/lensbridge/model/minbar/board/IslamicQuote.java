package com.ibrasoft.lensbridge.model.minbar.board;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "islamic_quotes")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IslamicQuote {
    public enum Kind { VERSE, HADITH }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_content_id", nullable = false)
    @JsonIgnore
    private WeeklyContent weeklyContent;

    @Enumerated(EnumType.STRING)
    private Kind kind;
    private String arabic;
    private String transliteration;
    private String translation;
    private String reference;

    /**
     * How long this quote's slide is pinned for, or null for "auto" — the board then sizes the
     * dwell time to the text it is actually rendering.
     * <p>
     * Per-quote rather than per-device, like {@link Poster#getDuration()}:
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
