package com.ibrasoft.lensbridge.dto.ticket;

import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderItemResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.PaymentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Minbar order response strips sensitive fields from the core DTO.
 */
class MinbarOrderResponseTest {

    @Test
    void stripsExternalRefAndProviderId() {
        OrderResponse core = OrderResponse.builder()
                .id(UUID.randomUUID())
                .buyerEmail("buyer@example.com")
                .externalRef("secret-user-uuid")
                .eventId(UUID.randomUUID())
                .status("AWAITING_PAYMENT")
                .providerId("interac")
                .referenceCode("REF-123")
                .amountTotal(new BigDecimal("25.00"))
                .currency("CAD")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .paidAt(null)
                .items(List.of())
                .build();

        MinbarOrderResponse minbar = MinbarOrderResponse.from(core);

        assertThat(minbar.getId()).isEqualTo(core.getId());
        assertThat(minbar.getBuyerEmail()).isEqualTo("buyer@example.com");
        assertThat(minbar.getStatus()).isEqualTo("AWAITING_PAYMENT");
        assertThat(minbar.getReferenceCode()).isEqualTo("REF-123");
        assertThat(minbar.getAmountTotal()).isEqualByComparingTo("25.00");
        assertThat(minbar.getCurrency()).isEqualTo("CAD");

        assertThat(minbar.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("externalRef", "providerId", "eventId");
    }

    @Test
    void mapsItemsStrippingInternalIds() {
        OrderItemResponse coreItem = OrderItemResponse.builder()
                .id(UUID.randomUUID())
                .ticketTypeId(UUID.randomUUID())
                .ticketTypeName("VIP")
                .attendeeFirstName("Jane")
                .attendeeLastName("Doe")
                .attendeeEmail("jane@example.com")
                .unitPrice(new BigDecimal("15.00"))
                .build();

        OrderResponse core = OrderResponse.builder()
                .id(UUID.randomUUID())
                .status("PAID")
                .items(List.of(coreItem))
                .build();

        MinbarOrderResponse minbar = MinbarOrderResponse.from(core);

        assertThat(minbar.getItems()).hasSize(1);
        MinbarOrderItemResponse item = minbar.getItems().get(0);
        assertThat(item.getTicketTypeName()).isEqualTo("VIP");
        assertThat(item.getAttendeeFirstName()).isEqualTo("Jane");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("15.00");

        assertThat(MinbarOrderItemResponse.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("id", "ticketTypeId", "attendeeEmail");
    }

    @Test
    void mapsPaymentStrippingProviderRef() {
        PaymentResponse corePayment = PaymentResponse.builder()
                .type("instructions")
                .providerRef("internal-provider-ref-123")
                .instructions("Send e-Transfer to pay@example.com")
                .details(Map.of("referenceCode", "REF-123"))
                .build();

        OrderResponse core = OrderResponse.builder()
                .id(UUID.randomUUID())
                .status("AWAITING_PAYMENT")
                .items(List.of())
                .payment(corePayment)
                .build();

        MinbarOrderResponse minbar = MinbarOrderResponse.from(core);

        assertThat(minbar.getPayment()).isNotNull();
        assertThat(minbar.getPayment().getType()).isEqualTo("instructions");
        assertThat(minbar.getPayment().getInstructions()).isEqualTo("Send e-Transfer to pay@example.com");
        assertThat(minbar.getPayment().getDetails()).containsEntry("referenceCode", "REF-123");

        assertThat(MinbarPaymentResponse.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("providerRef");
    }

    @Test
    void nullPaymentMapsToNull() {
        OrderResponse core = OrderResponse.builder()
                .id(UUID.randomUUID())
                .status("PAID")
                .items(List.of())
                .payment(null)
                .build();

        assertThat(MinbarOrderResponse.from(core).getPayment()).isNull();
    }

    @Test
    void redirectUrlIsPreserved() {
        PaymentResponse corePayment = PaymentResponse.builder()
                .type("redirect")
                .redirectUrl("https://checkout.stripe.com/session/abc")
                .build();

        OrderResponse core = OrderResponse.builder()
                .id(UUID.randomUUID())
                .status("AWAITING_PAYMENT")
                .items(List.of())
                .payment(corePayment)
                .build();

        MinbarOrderResponse minbar = MinbarOrderResponse.from(core);
        assertThat(minbar.getPayment().getRedirectUrl())
                .isEqualTo("https://checkout.stripe.com/session/abc");
    }
}
