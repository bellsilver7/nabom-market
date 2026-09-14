package com.example.nabom_market.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// @formatter:off
public record CartItemUpdateRequest(
        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 1 이상이어야 합니다.")
        int quantity
) {}
// @formatter:on
