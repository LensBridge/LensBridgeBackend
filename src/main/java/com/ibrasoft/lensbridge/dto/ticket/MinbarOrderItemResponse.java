package com.ibrasoft.lensbridge.dto.ticket;

import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class MinbarOrderItemResponse {
    private String ticketTypeName;
    private String attendeeFirstName;
    private String attendeeLastName;
    private BigDecimal unitPrice;

    public static MinbarOrderItemResponse from(OrderItemResponse item) {
        return MinbarOrderItemResponse.builder()
                .ticketTypeName(item.getTicketTypeName())
                .attendeeFirstName(item.getAttendeeFirstName())
                .attendeeLastName(item.getAttendeeLastName())
                .unitPrice(item.getUnitPrice())
                .build();
    }
}
