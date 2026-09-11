package com.example.nabom_market.cart.dto;

import com.example.nabom_market.cart.CartItem;

public record CartItemResponse(
                String productId,
                int quantity) {

        public static CartItemResponse from(CartItem cartItem) {
                return new CartItemResponse(cartItem.getProductId(), cartItem.getQuantity());
        }

}
