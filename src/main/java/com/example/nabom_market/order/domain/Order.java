package com.example.nabom_market.order.domain;

import java.time.LocalDateTime;

import com.example.nabom_market.common.exception.BusinessException;
import com.example.nabom_market.common.exception.ErrorCode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Order {
    Long id;
    Long memberId;
    int totalPrice;
    OrderStatus status;
    LocalDateTime orderedAt;
    LocalDateTime updatedAt;

    public Order(Long memberId, int totalPrice) {
        this.memberId = memberId;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PENDING;
    }

    public void cancel() {
        if (!status.isCancellable()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "취소할 수 없는 주문입니다. 현재 상태: " + status);
        }
        this.status = OrderStatus.CANCELLED;
    }
}
