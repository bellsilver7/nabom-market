package com.example.nabom_market.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 재발급과 로그아웃이 같은 모양을 쓴다. 둘 다 리프레시 토큰 하나로 판단한다. */
public record RefreshTokenRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.") String refreshToken) {
}
