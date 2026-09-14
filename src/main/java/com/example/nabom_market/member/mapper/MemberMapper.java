package com.example.nabom_market.member.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.example.nabom_market.member.domain.Member;

@Mapper
public interface MemberMapper {
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    void insert(Member member);
}
