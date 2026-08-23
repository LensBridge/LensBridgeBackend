package com.ibrasoft.lensbridge.dto.ticket;

import com.ibrasoft.tcketmanagebackend.model.dto.request.OrderItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MinbarCreateOrderRequest {

    @NotNull
    private UUID eventId;

    @Valid
    @NotEmpty
    private List<OrderItemRequest> items;
}
