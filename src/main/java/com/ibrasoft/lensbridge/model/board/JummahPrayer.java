package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "jummah_prayers")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JummahPrayer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_content_id", nullable = false)
    @JsonIgnore
    private WeeklyContent weeklyContent;

    private LocalTime prayerTime;
    private String khatib;
    private String room;
}
