package com.example.nabom_market.cart.dto;

import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        int totalPrice) {

    public static CartResponse of(Long cartId, List<CartItemResponse> items) {
        return new CartResponse(
                cartId, items, items.stream().mapToInt(CartItemResponse::totalPrice).sum());
    }
}
