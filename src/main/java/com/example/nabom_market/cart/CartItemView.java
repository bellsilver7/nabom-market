package com.example.nabom_market.cart;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CartItemView {

    private Long id;
    private Long productId;
    private String productName;
    private int productPrice;
    private int productStock;
    private int quantity;

    public int totalPrice() {
        return productPrice * quantity;
    }

    public boolean available() {
        return productStock >= quantity && productStock > 0;
    }
}
