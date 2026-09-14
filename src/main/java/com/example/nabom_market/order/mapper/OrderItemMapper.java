package com.example.nabom_market.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.nabom_market.order.domain.OrderItem;
import com.example.nabom_market.order.domain.OrderItemView;

@Mapper
public interface OrderItemMapper {
    List<OrderItemView> findAllByOrderId(@Param("orderId") Long orderId);

    List<OrderItemView> findAllByOrderIds(@Param("orderIds") List<Long> orderIds);

    Integer insert(OrderItem item);

    void bulkInsert(@Param("items") List<OrderItem> items);
}
