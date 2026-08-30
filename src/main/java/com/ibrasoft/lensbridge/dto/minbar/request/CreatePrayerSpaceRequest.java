package com.ibrasoft.lensbridge.dto.minbar.request;

import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.PrayerSpaceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.URL;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrayerSpaceRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be 255 characters or fewer")
    private String name;

    @NotBlank(message = "Tag is required")
    @Size(max = 255, message = "Tag must be 255 characters or fewer")
    private String tag;

    @NotBlank(message = "Building is required")
    @Size(max = 255, message = "Building must be 255 characters or fewer")
    private String building;

    @Size(max = 255)
    private String floor;

    @Size(max = 255)
    private String roomInfo;

    @Positive(message = "Capacity must be positive")
    private Integer capacity;

    @NotNull(message = "Type is required")
    private PrayerSpaceType type;

    /**
     * Optional. Omitted, it follows from {@code type} — a brothers' musallah is listed
     * to brothers, a reflection bay to everyone. Set it only to override that.
     */
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

    /** Prose directions, shown when no {@code steps} have been written. */
    private String directions;

    @Size(max = 255)
    private String startingPoint;

    @PositiveOrZero(message = "Walk time cannot be negative")
    private Integer walkTimeMinutes;

    @Size(max = 255)
    private String entranceName;

    @Size(max = 255)
    private String entranceDescription;

    @Builder.Default
    private List<@NotBlank @Size(max = 255) String> amenities = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<DirectionStepRequest> steps = new ArrayList<>();

    @Builder.Default
    private List<@NotBlank @Size(max = 255) String> tips = new ArrayList<>();
}
