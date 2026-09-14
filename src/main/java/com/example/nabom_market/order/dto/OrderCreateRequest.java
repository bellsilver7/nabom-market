package com.example.nabom_market.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// @formatter:off
public record OrderCreateRequest (
    @Valid
    @NotEmpty(message = "주문 항목은 최소 하나 이상이어야 합니다.")
    @Size(max = 50, message = "한 번에 주문할 수 있는 항목은 50개까지입니다.")
    List<OrderItemCreateRequest> items
) {}
// @formatter:on
