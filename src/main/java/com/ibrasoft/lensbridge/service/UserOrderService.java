package com.ibrasoft.lensbridge.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibrasoft.lensbridge.dto.ticket.MinbarCreateOrderRequest;
import com.ibrasoft.lensbridge.dto.ticket.MinbarOrderResponse;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.service.order.OrderAccessPolicy;
import com.ibrasoft.tcketmanagebackend.service.order.OrderCreationResult;
import com.ibrasoft.tcketmanagebackend.service.order.OrderService;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnProperty(prefix = "tcketmanage", name = "enabled")
@RequiredArgsConstructor
public class UserOrderService {

    private static final Comparator<Order> NEWEST_FIRST =
            Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<MinbarOrderResponse> getOrdersForUser(UUID userId) {
        return orderRepository.findByExternalRef(userId.toString()).stream()
                .sorted(NEWEST_FIRST)
                .map(OrderResponse::from)
                .map(MinbarOrderResponse::from)
                .toList();
    }

    public MinbarOrderResponse createOrderForUser(User user, MinbarCreateOrderRequest request) {
        CreateOrderRequest coreRequest = new CreateOrderRequest();
        coreRequest.setBuyerEmail(user.getEmail());
        coreRequest.setEventId(request.getEventId());
        coreRequest.setItems(request.getItems());

        OrderCreationResult result = orderService.createOrder(coreRequest);
        return MinbarOrderResponse.from(OrderResponse.from(result.order(), result.initiation()));
    }

    @Transactional(readOnly = true)
    public MinbarOrderResponse getOrderForUser(UUID userId, UUID orderId) {
        Order order = orderService.getOrder(orderId);
        accessPolicy.requireAccess(order.getExternalRef(), "Order");
        return MinbarOrderResponse.from(OrderResponse.from(order));
    }

    public MinbarOrderResponse cancelOrderForUser(UUID userId, UUID orderId) {
        accessPolicy.requireAccess(orderService.getOrder(orderId).getExternalRef(), "Order");
        Order cancelled = orderService.cancelOrder(orderId);
        return MinbarOrderResponse.from(OrderResponse.from(cancelled));
    }
}
