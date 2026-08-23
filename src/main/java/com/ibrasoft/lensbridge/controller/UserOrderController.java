package com.ibrasoft.lensbridge.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibrasoft.lensbridge.dto.ticket.BoardingPassView;
import com.ibrasoft.lensbridge.dto.ticket.MinbarCreateOrderRequest;
import com.ibrasoft.lensbridge.dto.ticket.MinbarOrderResponse;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.security.CurrentUser;
import com.ibrasoft.lensbridge.service.UserOrderService;
import com.ibrasoft.lensbridge.service.UserTicketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user")
@ConditionalOnProperty(prefix = "tcketmanage", name = "enabled")
@RequiredArgsConstructor
public class UserOrderController {

    private final UserOrderService userOrderService;
    private final UserTicketService userTicketService;

    @GetMapping("/orders")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<List<MinbarOrderResponse>> getUserOrders(@CurrentUser User user) {
        return ResponseEntity.ok(userOrderService.getOrdersForUser(user.getId()));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<MinbarOrderResponse> getOrder(@CurrentUser User user, @PathVariable UUID id) {
        return ResponseEntity.ok(userOrderService.getOrderForUser(user.getId(), id));
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<MinbarOrderResponse> createOrder(@CurrentUser User user,
                                                           @Valid @RequestBody MinbarCreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userOrderService.createOrderForUser(user, request));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<MinbarOrderResponse> cancelOrder(@CurrentUser User user, @PathVariable UUID id) {
        return ResponseEntity.ok(userOrderService.cancelOrderForUser(user.getId(), id));
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasRole('" + Role.Authority.USER + "')")
    public ResponseEntity<List<BoardingPassView>> getUserTickets(@CurrentUser User user) {
        return ResponseEntity.ok(userTicketService.getTicketsForUser(user.getId()));
    }
}
