package com.ibrasoft.lensbridge.dto.ticket;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MinbarTicketTypeViewTest {

    private static final Instant NOW = Instant.parse("2026-09-15T18:00:00Z");
    private static final Event STUB_EVENT = Event.builder().id(UUID.randomUUID()).name("E").build();

    private static TicketType.TicketTypeBuilder base() {
        return TicketType.builder()
                .id(UUID.randomUUID())
                .event(STUB_EVENT)
                .name("General Admission")
                .price(BigDecimal.TEN)
                .isActive(true)
                .capacity(100)
                .reservedCount(0);
    }

    // ==================== soldOut ====================

    @Test
    void availableTicketIsNotSoldOut() {
        TicketType tt = base().build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isFalse();
    }

    @Test
    void soldOutWhenCapacityReached() {
        TicketType tt = base().capacity(50).reservedCount(50).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isTrue();
    }

    @Test
    void soldOutWhenOverbooked() {
        TicketType tt = base().capacity(50).reservedCount(51).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isTrue();
    }

    @Test
    void soldOutWhenInactive() {
        TicketType tt = base().isActive(false).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isTrue();
    }

    @Test
    void soldOutWhenPastSalesEnd() {
        TicketType tt = base().salesEndAt(NOW.minus(1, ChronoUnit.HOURS)).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isTrue();
    }

    @Test
    void soldOutWhenSalesEndIsExactlyNow() {
        TicketType tt = base().salesEndAt(NOW).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isTrue();
    }

    @Test
    void soldOutWhenBeforeSalesStart() {
        TicketType tt = base().salesStartAt(NOW.plus(1, ChronoUnit.HOURS)).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isTrue();
    }

    @Test
    void notSoldOutWhenUnlimitedCapacity() {
        TicketType tt = base().capacity(null).reservedCount(9999).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isFalse();
    }

    @Test
    void notSoldOutWhenNoSalesWindow() {
        TicketType tt = base().salesStartAt(null).salesEndAt(null).build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isFalse();
    }

    @Test
    void notSoldOutWhenWithinSalesWindow() {
        TicketType tt = base()
                .salesStartAt(NOW.minus(1, ChronoUnit.DAYS))
                .salesEndAt(NOW.plus(1, ChronoUnit.DAYS))
                .build();
        assertThat(MinbarTicketTypeView.from(tt, NOW).isSoldOut()).isFalse();
    }

    // ==================== visibleFrom filtering ====================

    @Test
    void visibleFromExcludesInactiveTypes() {
        List<TicketType> types = List.of(
                base().name("Active").isActive(true).build(),
                base().name("Inactive").isActive(false).build());
        List<MinbarTicketTypeView> views = MinbarTicketTypeView.visibleFrom(types, NOW);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).getName()).isEqualTo("Active");
    }

    @Test
    void visibleFromExcludesTypesBeforeSalesStart() {
        List<TicketType> types = List.of(
                base().name("OnSale").salesStartAt(NOW.minus(1, ChronoUnit.HOURS)).build(),
                base().name("NotYet").salesStartAt(NOW.plus(1, ChronoUnit.HOURS)).build());
        List<MinbarTicketTypeView> views = MinbarTicketTypeView.visibleFrom(types, NOW);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).getName()).isEqualTo("OnSale");
    }

    @Test
    void visibleFromExcludesTypesPastSalesEnd() {
        List<TicketType> types = List.of(
                base().name("Open").salesEndAt(NOW.plus(1, ChronoUnit.HOURS)).build(),
                base().name("Closed").salesEndAt(NOW.minus(1, ChronoUnit.HOURS)).build());
        List<MinbarTicketTypeView> views = MinbarTicketTypeView.visibleFrom(types, NOW);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).getName()).isEqualTo("Open");
    }

    @Test
    void visibleFromExcludesTypesWhoseSalesEndIsExactlyNow() {
        List<TicketType> types = List.of(
                base().name("Expired").salesEndAt(NOW).build());
        assertThat(MinbarTicketTypeView.visibleFrom(types, NOW)).isEmpty();
    }

    @Test
    void visibleFromIncludesTypesWithNoSalesWindow() {
        List<TicketType> types = List.of(
                base().name("Always").salesStartAt(null).salesEndAt(null).build());
        assertThat(MinbarTicketTypeView.visibleFrom(types, NOW)).hasSize(1);
    }

    @Test
    void visibleFromReturnsSoldOutForCapacityReachedButStillVisible() {
        List<TicketType> types = List.of(
                base().name("Full").capacity(10).reservedCount(10).build());
        List<MinbarTicketTypeView> views = MinbarTicketTypeView.visibleFrom(types, NOW);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).isSoldOut()).isTrue();
    }

    // ==================== Field mapping ====================

    @Test
    void fromDoesNotExposeCapacityOrReservedCount() throws Exception {
        TicketType tt = base().capacity(100).reservedCount(42).build();
        MinbarTicketTypeView view = MinbarTicketTypeView.from(tt, NOW);
        assertThat(view).hasNoNullFieldsOrPropertiesExcept("salesStartAt", "salesEndAt");
        assertThat(view.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("capacity", "reservedCount", "createdAt", "isActive", "entitlements");
    }

    @Test
    void fromMapsExpectedFields() {
        Instant start = NOW.minus(1, ChronoUnit.DAYS);
        Instant end = NOW.plus(1, ChronoUnit.DAYS);
        UUID id = UUID.randomUUID();
        TicketType tt = base().id(id).name("VIP").price(new BigDecimal("25.00"))
                .salesStartAt(start).salesEndAt(end).build();
        MinbarTicketTypeView view = MinbarTicketTypeView.from(tt, NOW);
        assertThat(view.getId()).isEqualTo(id);
        assertThat(view.getName()).isEqualTo("VIP");
        assertThat(view.getPrice()).isEqualByComparingTo("25.00");
        assertThat(view.getSalesStartAt()).isEqualTo(start);
        assertThat(view.getSalesEndAt()).isEqualTo(end);
    }
}
