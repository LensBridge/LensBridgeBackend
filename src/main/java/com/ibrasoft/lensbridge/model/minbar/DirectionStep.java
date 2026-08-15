package com.ibrasoft.lensbridge.model.minbar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "prayer_space_steps")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DirectionStep {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prayer_space_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_prayer_space_steps_prayer_space_id"))
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PrayerSpace prayerSpace;

    @Column(nullable = false)
    private Integer stepOrder;

    @Column(nullable = false)
    private String instruction;

    private String subtext;
}
