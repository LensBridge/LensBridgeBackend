package com.ibrasoft.lensbridge.dto.board.response.frames;

import com.ibrasoft.lensbridge.dto.ticket.MinbarTicketTypeView;
import com.ibrasoft.lensbridge.model.minbar.Audience;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventView {
    private UUID id;
    private String name;
    private String description;
    private String location;
    private Instant startTime;
    private Instant endTime;
    private Boolean allDay;
    private Audience audience;
    private UUID ticketEventId;
    private List<MinbarTicketTypeView> ticketTypes;

    public static EventView of(BoardEvent event) {
        return of(event, null);
    }

    /**
     * Project a BoardEvent onto a more restrictive EventView for consumption by the frontend
     * @param event Event in question
     * @param types TicketTypes for said event
     * @return
     */
    public static EventView of(BoardEvent event, List<TicketType> types) {
        EventViewBuilder b = EventView.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .location(event.getLocation())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .allDay(event.getAllDay())
                .audience(event.getAudience());

        if (event.getEvent() != null) {
            b.ticketEventId(event.getEvent().getId());
        }

        // TODO: Decide if brothers/sisters will be able to see/purchase
        // Each ones respective ticket types.
        if (types != null && !types.isEmpty()) {
            b.ticketTypes(MinbarTicketTypeView.visibleFrom(types, Instant.now()));
        }

        return b.build();
    }
}
