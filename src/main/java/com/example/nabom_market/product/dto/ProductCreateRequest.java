package com.example.nabom_market.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.example.nabom_market.product.Product;

// @formatter:off
public record ProductCreateRequest(

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하로 입력해주세요.")
        String name,

        @NotNull(message = "가격은 필수입니다.")
        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        Integer price,

        @NotNull(message = "재고는 필수입니다.")
        @PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
        Integer stock
) {
    public Product toEntity() {
        return new Product(name, price, stock);
    }
}
// @formatter:on
