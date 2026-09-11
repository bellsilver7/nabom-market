package com.example.nabom_market.cart.dto;

import java.util.List;

import com.example.nabom_market.cart.Cart;

public record CartResponse(
        Long id,
        Long memberId,
        java.time.LocalDateTime createdAt,
        List<CartItemResponse> items) {

    public static CartResponse from(Cart cart, List<CartItemResponse> items) {
        return new CartResponse(
                cart.getId(),
                cart.getMemberId(),
                cart.getCreatedAt(),
                items);
    }
}
