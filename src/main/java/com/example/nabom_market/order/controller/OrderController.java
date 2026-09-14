package com.example.nabom_market.order.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.nabom_market.common.security.LoginMember;
import com.example.nabom_market.order.dto.OrderCreateRequest;
import com.example.nabom_market.order.dto.OrderResponse;
import com.example.nabom_market.order.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@LoginMember Long memberId,
            @Valid @RequestBody OrderCreateRequest request) {
        return orderService.create(memberId, request);
    }

    @GetMapping
    public List<OrderResponse> findAll(@LoginMember Long memberId) {
        return orderService.findAll(memberId);
    }

    @GetMapping("/{orderId}")
    public OrderResponse findById(@LoginMember Long memberId, @PathVariable Long orderId) {
        return orderService.findById(memberId, orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@LoginMember Long memberId, @PathVariable Long orderId) {
        return orderService.cancel(memberId, orderId);
    }

}
