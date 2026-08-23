package com.ibrasoft.lensbridge.dto.ticket;

import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
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
public class MinbarOrderResponse {
    private UUID id;
    private String status;
    private String buyerEmail;
    private BigDecimal amountTotal;
    private String currency;
    private String referenceCode;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant paidAt;
    private List<MinbarOrderItemResponse> items;
    private MinbarPaymentResponse payment;

    public static MinbarOrderResponse from(OrderResponse order) {
        List<MinbarOrderItemResponse> items = order.getItems() == null ? List.of()
                : order.getItems().stream().map(MinbarOrderItemResponse::from).toList();
        return MinbarOrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .buyerEmail(order.getBuyerEmail())
                .amountTotal(order.getAmountTotal())
                .currency(order.getCurrency())
                .referenceCode(order.getReferenceCode())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .items(items)
                .payment(MinbarPaymentResponse.from(order.getPayment()))
                .build();
    }
}
