package com.example.nabom_market.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.nabom_market.order.domain.Order;
import com.example.nabom_market.order.domain.OrderStatus;

public record OrderResponse(
        Long id,
        OrderStatus status,
        LocalDateTime orderedAt,
        int totalPrice,
        List<OrderItemResponse> items) {

    public static OrderResponse of(Order order, List<OrderItemResponse> items) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getOrderedAt(),
                items.stream().mapToInt(OrderItemResponse::totalPrice).sum(), items);
    }

}
