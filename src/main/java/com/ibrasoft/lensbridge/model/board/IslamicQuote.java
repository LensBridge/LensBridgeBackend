package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "islamic_quotes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
