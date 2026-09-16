package com.example.nabom_market.auth.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefreshToken {

    Long id;
    Long memberId;
    String tokenHash;
    LocalDateTime expiresAt;
    LocalDateTime revokedAt;
    LocalDateTime createdAt;

    public RefreshToken(Long memberId, String tokenHash, LocalDateTime expiresAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public boolean isUsable(LocalDateTime now) {
        return !isRevoked() && !isExpired(now);
    }

}
