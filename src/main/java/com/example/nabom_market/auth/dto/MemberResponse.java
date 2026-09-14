package com.example.nabom_market.auth.dto;

import com.example.nabom_market.member.domain.Member;

public record MemberResponse(
        Long id,
        String email,
        String name) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getName());
    }
}
