package com.ibrasoft.lensbridge.model.minbar;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A place on campus someone can pray.
 * <p>
 * The directions fields are stored as the parts they are made of — a starting point, a
 * walk time, an entrance, ordered steps — rather than as the sentences an app renders
 * from them. A client composes "2 min walk from the CCT main entrance"; the database
 * holds {@code startingPoint = "the CCT main entrance"} and {@code walkTimeMinutes = 2}.
 * That is the same split the board payload uses, and it is what lets the console edit a
 * walk time without re-parsing prose.
 */
@Entity
@Table(name = "prayer_spaces")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PrayerSpace {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Short caption under the name, e.g. "Main brothers' space". */
    @Column(nullable = false)
    private String tag;

    private String floor;

    @Column(nullable = false)
    private String building;

    /** Where in the building, in words: "Between offices 3026 & 3028". */
    @Column(name = "room_info")
    private String roomInfo;

    private Integer capacity;

    /**
     * What kind of space this is — the label and the map filter.
     * See {@link PrayerSpaceType} for why this is not {@link #audience}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false)
    private PrayerSpaceType spaceType;

    /** Who the space is listed for. Defaults from {@link #spaceType} on create. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    /**
     * Nullable, and the app has to cope: a reflection bay tucked inside a building has no
     * meaningful pin, and a space can be listed before anyone has stood in it with a
     * phone. Without both halves there is no distance, no marker, and no maps link.
     */
    private Double latitude;

    private Double longitude;

    private String mapsUrl;

    @Column(name = "image_url")
    private String imageUrl;

    /** Free-text operational note: "Gets busy during Dhuhr and Jummah." */
    @Column(columnDefinition = "TEXT")
    private String notes;

    // --- Directions metadata ---

    /**
     * Prose directions, for a space nobody has written {@link #steps} for yet. The app
     * falls back to this when the step list is empty, so a new space is useful the moment
     * it is created rather than only once someone has walked it.
     */
    @Column(columnDefinition = "TEXT")
    private String directions;

    private String startingPoint;

    private Integer walkTimeMinutes;

    private String entranceName;

    private String entranceDescription;

    // --- Collections ---

    @BatchSize(size = 32)
    @ElementCollection
    @CollectionTable(name = "prayer_space_amenities",
            joinColumns = @JoinColumn(name = "prayer_space_id"),
            foreignKey = @ForeignKey(name = "fk_prayer_space_amenities_prayer_space_id"))
    @Column(name = "amenity", nullable = false)
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<String> amenities = new ArrayList<>();

    @BatchSize(size = 32)
    @OneToMany(mappedBy = "prayerSpace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<DirectionStep> steps = new ArrayList<>();

    @BatchSize(size = 32)
    @ElementCollection
    @CollectionTable(name = "prayer_space_tips",
            joinColumns = @JoinColumn(name = "prayer_space_id"),
            foreignKey = @ForeignKey(name = "fk_prayer_space_tips_prayer_space_id"))
    @Column(name = "tip", nullable = false)
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<String> tips = new ArrayList<>();

    /**
     * Replaces the walking directions with {@code replacement}, renumbering as it goes.
     * <p>
     * Mutates the existing list rather than assigning a new one: {@code orphanRemoval}
     * tracks the collection instance Hibernate handed us, and swapping it out is what
     * raises "a collection with cascade=all-delete-orphan was no longer referenced".
     * Steps have no identity worth preserving across an edit — nothing links to a step —
     * so replacing wholesale is both correct and simpler than diffing by id.
     */
    public void replaceSteps(List<DirectionStep> replacement) {
        steps.clear();
        int order = 0;
        for (DirectionStep step : replacement) {
            step.setPrayerSpace(this);
            step.setStepOrder(order++);
            steps.add(step);
        }
    }
}
