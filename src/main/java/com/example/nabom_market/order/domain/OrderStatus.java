package com.example.nabom_market.order.domain;

public enum OrderStatus {
    PENDING,
    CANCELLED;

    public boolean isCancellable() {
        return this == PENDING;
    }
}
