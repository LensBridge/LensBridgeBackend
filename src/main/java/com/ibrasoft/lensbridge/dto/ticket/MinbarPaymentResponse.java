package com.ibrasoft.lensbridge.dto.ticket;

import com.ibrasoft.tcketmanagebackend.model.dto.response.PaymentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class MinbarPaymentResponse {
    private String type;
    private String redirectUrl;
    private String instructions;
    private Map<String, String> details;

    public static MinbarPaymentResponse from(PaymentResponse payment) {
        if (payment == null) return null;
        return MinbarPaymentResponse.builder()
                .type(payment.getType())
                .redirectUrl(payment.getRedirectUrl())
                .instructions(payment.getInstructions())
                .details(payment.getDetails())
                .build();
    }
}
