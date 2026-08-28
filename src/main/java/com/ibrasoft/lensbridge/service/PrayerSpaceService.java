package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.minbar.request.CreatePrayerSpaceRequest;
import com.ibrasoft.lensbridge.dto.minbar.request.DirectionStepRequest;
import com.ibrasoft.lensbridge.dto.minbar.request.UpdatePrayerSpaceRequest;
import com.ibrasoft.lensbridge.dto.minbar.response.PrayerSpaceView;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.DirectionStep;
import com.ibrasoft.lensbridge.model.minbar.PrayerSpace;
import com.ibrasoft.lensbridge.repository.sql.PrayerSpaceRepository;
import com.ibrasoft.lensbridge.util.Patch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Prayer spaces, read and written.
 * <p>
 * Every method returns a {@link PrayerSpaceView} rather than the entity. Assembling the
 * view inside the transaction is the point: the collections are lazy, so a controller
 * handed the entity would get whatever the session happened to have initialized, which is
 * how the list endpoint ended up silently dropping the walking directions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrayerSpaceService {

    private final PrayerSpaceRepository prayerSpaceRepository;

    // ==================== Reads ====================

    /** What the app shows a signed-in user: their audience, plus everything open to both. */
    @Transactional(readOnly = true)
    public List<PrayerSpaceView> getSpacesForAudience(Audience audience) {
        return prayerSpaceRepository.findByAudienceOrBoth(audience).stream()
                .map(PrayerSpaceView::of)
                .toList();
    }

    /** Every space, for the console. */
    @Transactional(readOnly = true)
    public List<PrayerSpaceView> getAllSpaces() {
        return prayerSpaceRepository.findAllByOrderByNameAsc().stream()
                .map(PrayerSpaceView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrayerSpaceView getSpace(UUID id) {
        return PrayerSpaceView.of(findOrThrow(id));
    }

    // ==================== Writes ====================

    @Transactional
    public PrayerSpaceView create(CreatePrayerSpaceRequest request) {
        validateCoordinates(request.getLatitude(), request.getLongitude());

        PrayerSpace space = PrayerSpace.builder()
                .name(request.getName().trim())
                .tag(request.getTag().trim())
                .building(request.getBuilding().trim())
                .floor(blankToNull(request.getFloor()))
                .roomInfo(blankToNull(request.getRoomInfo()))
                .capacity(request.getCapacity())
                .spaceType(request.getType())
                // An unstated audience follows from the type; see PrayerSpaceType.
                .audience(request.getAudience() != null
                        ? request.getAudience()
                        : request.getType().defaultAudience())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .mapsUrl(blankToNull(request.getMapsUrl()))
                .imageUrl(blankToNull(request.getImageUrl()))
                .notes(blankToNull(request.getNotes()))
                .directions(blankToNull(request.getDirections()))
                .startingPoint(blankToNull(request.getStartingPoint()))
                .walkTimeMinutes(request.getWalkTimeMinutes())
                .entranceName(blankToNull(request.getEntranceName()))
                .entranceDescription(blankToNull(request.getEntranceDescription()))
                .amenities(trimmedCopy(request.getAmenities()))
                .tips(trimmedCopy(request.getTips()))
                .build();

        space.replaceSteps(toSteps(request.getSteps()));

        space = prayerSpaceRepository.save(space);
        log.info("Created prayer space: id={}, name={}", space.getId(), space.getName());
        return PrayerSpaceView.of(space);
    }

    @Transactional
    public PrayerSpaceView update(UUID id, UpdatePrayerSpaceRequest request) {
        PrayerSpace space = findOrThrow(id);

        Patch.apply(blankToNull(request.getName()), space::setName);
        Patch.apply(blankToNull(request.getTag()), space::setTag);
        Patch.apply(blankToNull(request.getBuilding()), space::setBuilding);
        Patch.apply(request.getCapacity(), space::setCapacity);
        Patch.apply(request.getType(), space::setSpaceType);
        Patch.apply(request.getAudience(), space::setAudience);
        Patch.apply(request.getWalkTimeMinutes(), space::setWalkTimeMinutes);

        // Optional text: an empty string clears the field, matching UpdatePosterRequest's
        // signupUrl. A space can lose its room number or its photo without being deleted
        // and recreated, and null still means "unchanged".
        clearable(request.getFloor(), space::setFloor);
        clearable(request.getRoomInfo(), space::setRoomInfo);
        clearable(request.getMapsUrl(), space::setMapsUrl);
        clearable(request.getImageUrl(), space::setImageUrl);
        clearable(request.getNotes(), space::setNotes);
        clearable(request.getDirections(), space::setDirections);
        clearable(request.getStartingPoint(), space::setStartingPoint);
        clearable(request.getEntranceName(), space::setEntranceName);
        clearable(request.getEntranceDescription(), space::setEntranceDescription);

        Patch.apply(request.getLatitude(), space::setLatitude);
        Patch.apply(request.getLongitude(), space::setLongitude);
        validateCoordinates(space.getLatitude(), space.getLongitude());

        // Lists replace wholesale. Mutated in place rather than reassigned: the amenity
        // and tip collections are @ElementCollection instances Hibernate is tracking, and
        // handing it a new ArrayList orphans the one it knows about.
        replaceInPlace(space.getAmenities(), request.getAmenities());
        replaceInPlace(space.getTips(), request.getTips());
        if (request.getSteps() != null) {
            space.replaceSteps(toSteps(request.getSteps()));
        }

        space = prayerSpaceRepository.save(space);
        log.info("Updated prayer space: id={}", id);
        return PrayerSpaceView.of(space);
    }

    @Transactional
    public void delete(UUID id) {
        PrayerSpace space = findOrThrow(id);
        prayerSpaceRepository.delete(space);
        log.info("Deleted prayer space: id={}, name={}", id, space.getName());
    }

    // ==================== Helpers ====================

    private PrayerSpace findOrThrow(UUID id) {
        return prayerSpaceRepository.findById(id)
                .orElseThrow(() -> new ApiResponseException(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse.of("Prayer space not found with id: " + id)));
    }

    /**
     * A pin needs both halves. One coordinate on its own is not a partial location, it is
     * a point somewhere off the coast of Africa, so reject it rather than store it and let
     * the map draw it.
     */
    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new ApiResponseException(
                    HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("Latitude and longitude must be provided together"));
        }
    }

    private static List<DirectionStep> toSteps(List<DirectionStepRequest> requests) {
        return orEmpty(requests).stream()
                .map(r -> DirectionStep.builder()
                        .instruction(r.getInstruction().trim())
                        .subtext(blankToNull(r.getSubtext()))
                        .build())
                .toList();
    }

    private static void replaceInPlace(List<String> target, List<String> replacement) {
        if (replacement == null) return;
        target.clear();
        target.addAll(trimmedCopy(replacement));
    }

    private static List<String> trimmedCopy(List<String> values) {
        List<String> copy = new ArrayList<>();
        for (String value : orEmpty(values)) {
            copy.add(value.trim());
        }
        return copy;
    }

    /** Applies a patch field where an empty string means "clear this". */
    private static void clearable(String value, Consumer<String> setter) {
        if (value == null) return;
        setter.accept(blankToNull(value));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> List<T> orEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }
}
