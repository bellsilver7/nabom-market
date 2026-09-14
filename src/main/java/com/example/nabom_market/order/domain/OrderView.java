package com.example.nabom_market.order.domain;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderView {
    Long id;
    Long memberId;
    Long totalPrice;
    OrderStatus status;
    LocalDateTime orderedAt;
    LocalDateTime updatedAt;
    List<OrderItemView> items;
}
