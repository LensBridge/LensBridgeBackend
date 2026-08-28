package com.ibrasoft.lensbridge.dto.minbar.request;

import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.PrayerSpaceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/**
 * A partial update. Null means "leave it alone" for every field, which is why nothing
 * here is {@code @NotNull} — see {@code UpdatePosterRequest} for the same idiom.
 * <p>
 * The list fields are the exception worth stating: sending {@code amenities} replaces the
 * whole list, and sending an empty list clears it. There is no per-item patch, because a
 * list of strings has no stable identity to patch against.
 * <p>
 * {@code audience} does <em>not</em> re-derive from {@code type} here. Changing a space's
 * type on an existing record should not silently re-list a sisters-only reflection bay to
 * the whole campus; if the audience should move too, send both.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePrayerSpaceRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String tag;

    @Size(max = 255)
    private String building;

    @Size(max = 255)
    private String floor;

    @Size(max = 255)
    private String roomInfo;

    @Positive(message = "Capacity must be positive")
    private Integer capacity;

    private PrayerSpaceType type;

    private Audience audience;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @URL(message = "Maps URL must be a valid URL")
    private String mapsUrl;

    @URL(message = "Image URL must be a valid URL")
    private String imageUrl;

    private String notes;

    private String directions;

    @Size(max = 255)
    private String startingPoint;

    @PositiveOrZero(message = "Walk time cannot be negative")
    private Integer walkTimeMinutes;

    @Size(max = 255)
    private String entranceName;

    @Size(max = 255)
    private String entranceDescription;

    /** Replaces the whole list when present. */
    private List<@NotBlank @Size(max = 255) String> amenities;

    /** Replaces the whole list when present. */
    @Valid
    private List<DirectionStepRequest> steps;

    /** Replaces the whole list when present. */
    private List<@NotBlank @Size(max = 255) String> tips;
}
