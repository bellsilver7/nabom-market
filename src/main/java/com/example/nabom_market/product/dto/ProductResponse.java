package com.example.nabom_market.product.dto;

import java.time.LocalDateTime;

import com.example.nabom_market.product.Product;

public record ProductResponse(
        Long id,
        String name,
        Integer price,
        Integer stock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
