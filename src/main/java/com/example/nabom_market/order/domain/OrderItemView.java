package com.example.nabom_market.order.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderItemView {

    Long id;
    Long orderId;
    Long productId;
    String productName;
    Integer quantity;
    Integer orderPrice;

    public int totalPrice() {
        return orderPrice * quantity;
    }

}
