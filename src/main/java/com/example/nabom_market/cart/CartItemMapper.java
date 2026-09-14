package com.example.nabom_market.cart;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartItemMapper {

    void upsert(CartItem cartItem);

    int updateQuantity(@Param("id") Long id, @Param("quantity") int quantity);

    boolean deleteByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

    boolean existsById(Long id);

    Optional<CartItem> findById(Long id);

    List<CartItemView> findByMemberId(@Param("memberId") Long memberId);

    Optional<CartItemView> findByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);
}
