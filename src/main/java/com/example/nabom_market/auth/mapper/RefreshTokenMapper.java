package com.example.nabom_market.auth.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.example.nabom_market.auth.domain.RefreshToken;

@Mapper
public interface RefreshTokenMapper {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void insert(RefreshToken refreshToken);

    boolean revokeIfActive(String tokenHash);

    void revokeAllByMemberId(Long memberId);

}
