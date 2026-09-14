package com.example.nabom_market.order.dto;

import com.example.nabom_market.order.domain.OrderItemView;

public record OrderItemResponse(

        Long id,
        Long productId,
        String productName,
        Integer orderPrice,
        Integer quantity,
        Integer totalPrice) {

    public static OrderItemResponse from(OrderItemView item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getProductName(), item.getOrderPrice(),
                item.getQuantity(), item.totalPrice());
    }

}
