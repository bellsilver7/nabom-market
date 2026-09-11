package com.example.nabom_market.product;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Product {

    private Long id;
    private String name;
    private Integer price;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product(String name, Integer price, Integer stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public void update(String name, Integer price, Integer stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }
}
