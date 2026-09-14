package com.example.nabom_market.cart;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    private Long id;
    private Long cartId;
    private Long productId;
    private int quantity;

    public CartItem(Long cartId, Long productId, Integer quantity) {

        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
