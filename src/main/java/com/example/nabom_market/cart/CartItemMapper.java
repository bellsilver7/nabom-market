package com.example.nabom_market.cart;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper {

    void insert(CartItem cartItem);

    int update(CartItem cartItem);

    int deleteById(Long id);

    Optional<CartItem> findById(Long id);

    List<CartItem> findByCartId(Long cartId);
}
