package com.ibrasoft.lensbridge.dto.minbar.response;

import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.DirectionStep;
import com.ibrasoft.lensbridge.model.minbar.PrayerSpace;
import com.ibrasoft.lensbridge.model.minbar.PrayerSpaceType;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * A prayer space on the wire.
 * <p>
 * This exists because the endpoints used to return the entity. That leaked the JPA
 * mapping into the public contract — {@code steps} is a lazy {@code @OneToMany}, so
 * whether a caller got the walking directions depended on whether a transaction happened
 * to be open, which is why the admin console has a comment saying the list endpoint omits
 * them and re-fetches each space one at a time. A view assembled inside the service is
 * the same shape every time.
 * <p>
 * The fields are the stored parts, not rendered sentences: {@code walkTimeMinutes} and
 * {@code startingPoint} rather than "2 min walk from the CCT main entrance". Clients
 * compose the copy, which is what lets the app and the board word it differently without
 * the server picking a side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrayerSpaceView {

    private UUID id;
    private String name;
    private String tag;
    private String building;
    private String floor;
    private String roomInfo;
    private Integer capacity;
    private PrayerSpaceType type;
    private Audience audience;

    /** Null together with {@code longitude} when nobody has pinned the space yet. */
    private Double latitude;
    private Double longitude;

    private String mapsUrl;
    private String imageUrl;
    private String notes;

    /** Prose fallback, used when {@code steps} is empty. */
    private String directions;

    private String startingPoint;
    private Integer walkTimeMinutes;
    private String entranceName;
    private String entranceDescription;

    private List<String> amenities;
    private List<DirectionStepView> steps;
    private List<String> tips;

    /** One leg of the walk. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectionStepView {
        private UUID id;
        /** Zero-based position, so a client can render "step 3 of 5" without re-indexing. */
        private Integer order;
        private String instruction;
        /** The landmark that confirms you took the right turn. */
        private String subtext;
    }

    public static PrayerSpaceView of(PrayerSpace space) {
        return PrayerSpaceView.builder()
                .id(space.getId())
                .name(space.getName())
                .tag(space.getTag())
                .building(space.getBuilding())
                .floor(space.getFloor())
                .roomInfo(space.getRoomInfo())
                .capacity(space.getCapacity())
                .type(space.getSpaceType())
                .audience(space.getAudience())
                .latitude(space.getLatitude())
                .longitude(space.getLongitude())
                .mapsUrl(space.getMapsUrl())
                .imageUrl(space.getImageUrl())
                .notes(space.getNotes())
                .directions(space.getDirections())
                .startingPoint(space.getStartingPoint())
                .walkTimeMinutes(space.getWalkTimeMinutes())
                .entranceName(space.getEntranceName())
                .entranceDescription(space.getEntranceDescription())
                .amenities(List.copyOf(space.getAmenities()))
                .steps(space.getSteps().stream().map(PrayerSpaceView::stepOf).toList())
                .tips(List.copyOf(space.getTips()))
                .build();
    }

    private static DirectionStepView stepOf(DirectionStep step) {
        return DirectionStepView.builder()
                .id(step.getId())
                .order(step.getStepOrder())
                .instruction(step.getInstruction())
                .subtext(step.getSubtext())
                .build();
    }
}
