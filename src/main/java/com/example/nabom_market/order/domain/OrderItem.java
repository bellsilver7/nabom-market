package com.example.nabom_market.order.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    Long id;
    Long orderId;
    Long productId;
    Integer quantity;
    Integer orderPrice;

    public OrderItem(Long productId, Integer quantity, Integer orderPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.orderPrice = orderPrice;
    }

    public int totalPrice() {
        return orderPrice * quantity;
    }

    public void assignOrder(Long orderId) {
        this.orderId = orderId;
    }
}
