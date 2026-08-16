package com.ibrasoft.lensbridge.model.minbar;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "prayer_spaces")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PrayerSpace {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String tag;

    private String floor;

    @Column(nullable = false)
    private String building;

    private String location;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    private String mapsUrl;

    // --- Directions metadata ---

    private String startingPoint;

    private Integer walkTimeMinutes;

    private String entranceName;

    private String entranceDescription;

    // --- Collections ---

    @ElementCollection
    @CollectionTable(name = "prayer_space_amenities",
            joinColumns = @JoinColumn(name = "prayer_space_id"),
            foreignKey = @ForeignKey(name = "fk_prayer_space_amenities_prayer_space_id"))
    @Column(name = "amenity", nullable = false)
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<String> amenities = new ArrayList<>();

    @OneToMany(mappedBy = "prayerSpace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<DirectionStep> steps = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "prayer_space_tips",
            joinColumns = @JoinColumn(name = "prayer_space_id"),
            foreignKey = @ForeignKey(name = "fk_prayer_space_tips_prayer_space_id"))
    @Column(name = "tip", nullable = false)
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<String> tips = new ArrayList<>();
}
