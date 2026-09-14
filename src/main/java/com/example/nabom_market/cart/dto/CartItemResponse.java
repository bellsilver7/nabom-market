package com.example.nabom_market.cart.dto;

import com.example.nabom_market.cart.CartItemView;

public record CartItemResponse(
                Long id,
                Long productId,
                String productName,
                int productPrice,
                int quantity,
                int totalPrice,
                boolean available) {

        public static CartItemResponse from(CartItemView v) {
                return new CartItemResponse(
                                v.getId(),
                                v.getProductId(),
                                v.getProductName(),
                                v.getProductPrice(),
                                v.getQuantity(),
                                v.totalPrice(),
                                v.available());
        }
}
