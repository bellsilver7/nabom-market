package com.example.nabom_market.order.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Order {
    Long id;
    Long memberId;
    int totalPrice;
    OrderStatus status;
    LocalDateTime orderedAt;
    LocalDateTime updatedAt;

    public Order(Long memberId, int totalPrice) {
        this.memberId = memberId;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PENDING;
    }
}
