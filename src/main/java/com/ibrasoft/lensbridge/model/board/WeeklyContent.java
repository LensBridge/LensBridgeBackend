package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "weekly_content",
    uniqueConstraints = @UniqueConstraint(columnNames = {"year", "week_number"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyContent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @OneToMany(mappedBy = "weeklyContent", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<IslamicQuote> quotes = new ArrayList<>();

    @OneToMany(mappedBy = "weeklyContent", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderColumn(name = "slot_order")
    @Builder.Default
    private List<JummahPrayer> jummahPrayers = new ArrayList<>();
}
