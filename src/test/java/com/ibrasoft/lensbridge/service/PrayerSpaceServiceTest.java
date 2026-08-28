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
import com.ibrasoft.lensbridge.model.minbar.PrayerSpaceType;
import com.ibrasoft.lensbridge.repository.sql.PrayerSpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrayerSpaceServiceTest {

    @Mock
    private PrayerSpaceRepository prayerSpaceRepository;

    @InjectMocks
    private PrayerSpaceService service;

    private CreatePrayerSpaceRequest.CreatePrayerSpaceRequestBuilder validCreate() {
        return CreatePrayerSpaceRequest.builder()
                .name("Brother's Musallah")
                .tag("Main brothers' space")
                .building("Communication, Culture & Technology (CCT)")
                .type(PrayerSpaceType.BROTHERS);
    }

    private PrayerSpace existing() {
        PrayerSpace space = PrayerSpace.builder()
                .id(UUID.randomUUID())
                .name("Deerfield Hall Reflection Bay")
                .tag("Quiet bay")
                .building("Deerfield Hall")
                .floor("3rd Floor")
                .roomInfo("Between offices 3026 & 3028")
                .spaceType(PrayerSpaceType.REFLECTION)
                .audience(Audience.BOTH)
                .amenities(new ArrayList<>(List.of("Quiet space", "Seating")))
                .tips(new ArrayList<>(List.of("Bring your own prayer mat")))
                .steps(new ArrayList<>())
                .build();
        space.replaceSteps(List.of(
                DirectionStep.builder().instruction("Take the elevator to the 3rd floor").build(),
                DirectionStep.builder().instruction("Turn right").subtext("Past office 3024").build()));
        return space;
    }

    /**
     * The message a caller actually sees. ApiResponseException's two-argument constructor
     * passes null to super(), so the text lives in the body, not in getMessage().
     */
    private static String errorOf(Throwable e) {
        return ((ErrorResponse) ((ApiResponseException) e).getBody()).getError();
    }

    /** Returns whatever was handed to save(), so assertions read the entity the service built. */
    private void echoSave() {
        when(prayerSpaceRepository.save(any(PrayerSpace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==================== Audience derivation ====================

    @Test
    void createDerivesAudienceFromTypeWhenNotGiven() {
        echoSave();

        PrayerSpaceView created = service.create(validCreate().build());

        assertThat(created.getAudience()).isEqualTo(Audience.BROTHERS);
    }

    @Test
    void createDerivesBothForSpacesOpenToEveryone() {
        echoSave();

        assertThat(service.create(validCreate().type(PrayerSpaceType.MULTIFAITH).build()).getAudience())
                .isEqualTo(Audience.BOTH);
        assertThat(service.create(validCreate().type(PrayerSpaceType.REFLECTION).build()).getAudience())
                .isEqualTo(Audience.BOTH);
    }

    @Test
    void createHonoursAnExplicitAudienceOverTheTypeDefault() {
        echoSave();

        PrayerSpaceView created = service.create(
                validCreate().type(PrayerSpaceType.REFLECTION).audience(Audience.SISTERS).build());

        assertThat(created.getType()).isEqualTo(PrayerSpaceType.REFLECTION);
        assertThat(created.getAudience()).isEqualTo(Audience.SISTERS);
    }

    /**
     * Changing the type must not silently re-list the space. A reflection bay a building has
     * designated sisters-only would otherwise be published to the whole campus by an edit that
     * only meant to correct its label.
     */
    @Test
    void updateDoesNotReDeriveAudienceFromANewType() {
        PrayerSpace space = existing();
        space.setAudience(Audience.SISTERS);
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        PrayerSpaceView updated = service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder().type(PrayerSpaceType.MULTIFAITH).build());

        assertThat(updated.getType()).isEqualTo(PrayerSpaceType.MULTIFAITH);
        assertThat(updated.getAudience()).isEqualTo(Audience.SISTERS);
    }

    // ==================== Coordinates ====================

    @Test
    void createRejectsOneCoordinateWithoutTheOther() {
        assertThatThrownBy(() -> service.create(validCreate().latitude(43.5489).build()))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(errorOf(e)).contains("together"));

        verify(prayerSpaceRepository, never()).save(any());
    }

    @Test
    void createAcceptsAPairOfCoordinates() {
        echoSave();

        PrayerSpaceView created = service.create(
                validCreate().latitude(43.5489).longitude(-79.6632).build());

        assertThat(created.getLatitude()).isEqualTo(43.5489);
        assertThat(created.getLongitude()).isEqualTo(-79.6632);
    }

    @Test
    void createAcceptsNoCoordinatesAtAll() {
        echoSave();

        PrayerSpaceView created = service.create(validCreate().build());

        assertThat(created.getLatitude()).isNull();
        assertThat(created.getLongitude()).isNull();
    }

    /** The pair is validated against the merged state, not the patch, so half a pin cannot slip in. */
    @Test
    void updateRejectsALatitudeThatWouldLeaveTheSpaceHalfPinned() {
        PrayerSpace space = existing();
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));

        assertThatThrownBy(() -> service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder().latitude(43.55).build()))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(errorOf(e)).contains("together"));

        verify(prayerSpaceRepository, never()).save(any());
    }

    @Test
    void updateAcceptsALatitudeWhenTheSpaceAlreadyHasALongitude() {
        PrayerSpace space = existing();
        space.setLatitude(43.5000);
        space.setLongitude(-79.6620);
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        PrayerSpaceView updated = service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder().latitude(43.5501).build());

        assertThat(updated.getLatitude()).isEqualTo(43.5501);
        assertThat(updated.getLongitude()).isEqualTo(-79.6620);
    }

    // ==================== Patch semantics ====================

    @Test
    void updateLeavesOmittedFieldsAlone() {
        PrayerSpace space = existing();
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        PrayerSpaceView updated = service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder().capacity(6).build());

        assertThat(updated.getCapacity()).isEqualTo(6);
        assertThat(updated.getName()).isEqualTo("Deerfield Hall Reflection Bay");
        assertThat(updated.getFloor()).isEqualTo("3rd Floor");
        assertThat(updated.getAmenities()).containsExactly("Quiet space", "Seating");
        assertThat(updated.getSteps()).hasSize(2);
    }

    @Test
    void updateClearsAnOptionalTextFieldSentAsEmpty() {
        PrayerSpace space = existing();
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        PrayerSpaceView updated = service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder().roomInfo("").build());

        assertThat(updated.getRoomInfo()).isNull();
    }

    @Test
    void updateReplacesListsWholesaleAndAnEmptyListClearsThem() {
        PrayerSpace space = existing();
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        PrayerSpaceView updated = service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder()
                        .amenities(List.of("Prayer mats"))
                        .tips(List.of())
                        .build());

        assertThat(updated.getAmenities()).containsExactly("Prayer mats");
        assertThat(updated.getTips()).isEmpty();
    }

    /**
     * The amenity list is an {@code @ElementCollection} Hibernate is tracking. Handing it a
     * fresh ArrayList orphans the instance it knows about, so the service mutates in place —
     * this asserts it actually does.
     */
    @Test
    void updateMutatesTheTrackedCollectionRatherThanReplacingIt() {
        PrayerSpace space = existing();
        List<String> tracked = space.getAmenities();
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder().amenities(List.of("Wudu area nearby")).build());

        assertThat(space.getAmenities()).isSameAs(tracked);
        assertThat(tracked).containsExactly("Wudu area nearby");
    }

    @Test
    void updateRenumbersReplacementStepsFromZero() {
        PrayerSpace space = existing();
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        PrayerSpaceView updated = service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder()
                        .steps(List.of(
                                DirectionStepRequest.builder().instruction("Enter through the north doors").build(),
                                DirectionStepRequest.builder()
                                        .instruction("Take the stairs")
                                        .subtext("Not the elevator")
                                        .build(),
                                DirectionStepRequest.builder().instruction("Turn left").build()))
                        .build());

        assertThat(updated.getSteps()).extracting(PrayerSpaceView.DirectionStepView::getOrder)
                .containsExactly(0, 1, 2);
        assertThat(updated.getSteps()).extracting(PrayerSpaceView.DirectionStepView::getInstruction)
                .containsExactly("Enter through the north doors", "Take the stairs", "Turn left");
        assertThat(updated.getSteps().get(1).getSubtext()).isEqualTo("Not the elevator");
    }

    @Test
    void updateKeepsTheTrackedStepCollectionInstance() {
        PrayerSpace space = existing();
        List<DirectionStep> tracked = space.getSteps();
        when(prayerSpaceRepository.findById(space.getId())).thenReturn(Optional.of(space));
        echoSave();

        service.update(space.getId(),
                UpdatePrayerSpaceRequest.builder()
                        .steps(List.of(DirectionStepRequest.builder().instruction("Walk in").build()))
                        .build());

        assertThat(space.getSteps()).isSameAs(tracked);
        assertThat(tracked).hasSize(1);
        assertThat(tracked.get(0).getPrayerSpace()).isSameAs(space);
    }

    @Test
    void createTrimsWhitespaceAndDropsBlankOptionalText() {
        echoSave();

        PrayerSpaceView created = service.create(validCreate()
                .name("  Brother's Musallah  ")
                .roomInfo("   ")
                .amenities(List.of("  Prayer mats  "))
                .steps(List.of(DirectionStepRequest.builder()
                        .instruction("  Enter through the glass doors  ")
                        .subtext("  ")
                        .build()))
                .build());

        assertThat(created.getName()).isEqualTo("Brother's Musallah");
        assertThat(created.getRoomInfo()).isNull();
        assertThat(created.getAmenities()).containsExactly("Prayer mats");
        assertThat(created.getSteps().get(0).getInstruction()).isEqualTo("Enter through the glass doors");
        assertThat(created.getSteps().get(0).getSubtext()).isNull();
    }

    // ==================== Lookups ====================

    @Test
    void readingAMissingSpaceIsA404NotAnEmptyBody() {
        UUID id = UUID.randomUUID();
        when(prayerSpaceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSpace(id))
                .isInstanceOf(ApiResponseException.class)
                .satisfies(e -> assertThat(((ApiResponseException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deletingAMissingSpaceIsA404() {
        UUID id = UUID.randomUUID();
        when(prayerSpaceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ApiResponseException.class);
        verify(prayerSpaceRepository, never()).delete(any());
    }

    /**
     * The app's list used to omit the walking directions, because the endpoint returned a lazy
     * entity. Assembling the view in the service is what fixed that, so the list carrying steps
     * is worth pinning down.
     */
    @Test
    void theAudienceListCarriesFullDirections() {
        PrayerSpace space = existing();
        when(prayerSpaceRepository.findByAudienceOrBoth(Audience.SISTERS)).thenReturn(List.of(space));

        List<PrayerSpaceView> views = service.getSpacesForAudience(Audience.SISTERS);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).getSteps()).hasSize(2);
        assertThat(views.get(0).getSteps().get(1).getSubtext()).isEqualTo("Past office 3024");
    }
}
