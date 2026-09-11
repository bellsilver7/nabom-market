package com.example.nabom_market.cart;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartMapper {

    void insert(Cart cart);

    int update(Cart cart);

    int deleteById(Long id);

    Optional<Cart> findById(Long id);

}
