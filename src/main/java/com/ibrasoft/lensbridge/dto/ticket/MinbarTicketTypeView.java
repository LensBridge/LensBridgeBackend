package com.ibrasoft.lensbridge.dto.ticket;

import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class MinbarTicketTypeView {
    private UUID id;
    private String name;
    private BigDecimal price;
    private Instant salesStartAt;
    private Instant salesEndAt;
    private boolean soldOut;

    public static MinbarTicketTypeView from(TicketType tt, Instant now) {
        boolean soldOut = !Boolean.TRUE.equals(tt.getIsActive())
                || (tt.getCapacity() != null && tt.getReservedCount() != null && tt.getReservedCount() >= tt.getCapacity())
                || (tt.getSalesEndAt() != null && now.compareTo(tt.getSalesEndAt()) >= 0)
                || (tt.getSalesStartAt() != null && now.isBefore(tt.getSalesStartAt()));
        return MinbarTicketTypeView.builder()
                .id(tt.getId())
                .name(tt.getName())
                .price(tt.getPrice())
                .salesStartAt(tt.getSalesStartAt())
                .salesEndAt(tt.getSalesEndAt())
                .soldOut(soldOut)
                .build();
    }

    public static List<MinbarTicketTypeView> visibleFrom(List<TicketType> types, Instant now) {
        return types.stream()
                .filter(tt -> Boolean.TRUE.equals(tt.getIsActive()))
                .filter(tt -> tt.getSalesStartAt() == null || !now.isBefore(tt.getSalesStartAt()))
                .filter(tt -> tt.getSalesEndAt() == null || now.isBefore(tt.getSalesEndAt()))
                .map(tt -> from(tt, now))
                .toList();
    }
}
