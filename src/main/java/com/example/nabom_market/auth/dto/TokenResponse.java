package com.example.nabom_market.auth.dto;

/**
 * 토큰 한 쌍.
 *
 * <p>
 * {@code expiresIn} 은 액세스 토큰의 남은 초다. 리프레시 토큰의 수명은 알려주지 않는다 —
 * 클라이언트가 할 일은 액세스 토큰이 만료되면 재발급을 시도하는 것이고, 리프레시 토큰까지
 * 만료됐다면 그때 401 을 받고 로그인 화면으로 가면 된다.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
