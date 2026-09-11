package com.example.nabom_market.cart;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Cart {

    private Long id;
    private Long memberId;
    private java.time.LocalDateTime createdAt;

    public Cart(Long memberId) {
        this.memberId = memberId;
        this.createdAt = java.time.LocalDateTime.now();
    }
}
