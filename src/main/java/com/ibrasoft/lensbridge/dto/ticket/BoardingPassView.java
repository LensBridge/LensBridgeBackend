package com.ibrasoft.lensbridge.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
/**
 * Payload to be shown when a user opens a ticket
 *
 * NOTE: Eventually this functionality will be folded into tCketManage once I figure out how to get
 * mobile wallet support working
 */
public class BoardingPassView {
    private UUID ticketId;
    private UUID eventId;
    private String eventTitle;
    private String eventDate;
    private String startTime;
    private String location;
    private String ticketType;
    private String attendeeName;
    private String qrPayload;
    private String status;
}
