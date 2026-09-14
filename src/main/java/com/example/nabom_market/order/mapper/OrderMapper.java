package com.example.nabom_market.order.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.nabom_market.order.domain.Order;

@Mapper
public interface OrderMapper {
    Order findById(Long id);

    Optional<Order> findByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    List<Order> findAllByMemberId(@Param("memberId") Long memberId);

    void insert(Order order);

    void update(Order order);
}
