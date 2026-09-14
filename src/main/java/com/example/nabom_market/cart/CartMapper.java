package com.example.nabom_market.cart;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartMapper {

    void upsert(Long memberId);

    Cart findByMemberId(Long memberId);
}
