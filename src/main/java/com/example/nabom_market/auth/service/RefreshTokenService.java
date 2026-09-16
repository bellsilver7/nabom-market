package com.example.nabom_market.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.nabom_market.auth.domain.RefreshToken;
import com.example.nabom_market.auth.mapper.RefreshTokenMapper;
import com.example.nabom_market.common.exception.BusinessException;
import com.example.nabom_market.common.exception.ErrorCode;

/**
 * 리프레시 토큰 발급 · 회전 · 폐기.
 *
 * <p>
 * 액세스 토큰과 달리 이 토큰은 자기 자신을 증명하지 못한다. 서명이 없는 난수일 뿐이고,
 * 유효한지는 DB 에 같은 해시의 행이 살아 있는지로만 판단한다. 그래서 {@code JwtProvider}
 * 와 한 클래스에 두지 않는다 — 검증 방식도, 수명도, 무효화 가능 여부도 다르다.
 *
 * <p>
 * 원문은 어디에도 저장하지 않는다. 발급할 때 한 번 돌려주고, 그 뒤로 서버가 가진 것은
 * SHA-256 해시뿐이다.
 */
@Service
public class RefreshTokenService {

    /** 난수 길이. 32바이트 = 256비트면 추측으로 맞힐 수 없다. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenMapper refreshTokenMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long expirationDays;

    /**
     * 재사용 탐지의 연쇄 폐기 전용. 바깥 트랜잭션과 분리해 커밋한다.
     *
     * <p>
     * 폐기 직후 401 을 던지는데, 같은 트랜잭션이면 그 예외가 폐기까지 되돌린다.
     * 응답은 401 인데 DB 에는 토큰이 멀쩡히 살아 있는 상태가 된다.
     */
    private final TransactionTemplate revokeInNewTransaction;

    public RefreshTokenService(
            RefreshTokenMapper refreshTokenMapper,
            PlatformTransactionManager transactionManager,
            @Value("${jwt.refresh-expiration-days}") long expirationDays) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.expirationDays = expirationDays;
        this.revokeInNewTransaction = new TransactionTemplate(transactionManager);
        this.revokeInNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 새 리프레시 토큰을 발급하고 원문을 돌려준다. 원문을 볼 수 있는 것은 이 순간뿐이다. */
    @Transactional
    public String issue(Long memberId) {
        String token = randomToken();

        refreshTokenMapper.insert(new RefreshToken(
                memberId,
                hash(token),
                LocalDateTime.now().plusDays(expirationDays)));

        return token;
    }

    /**
     * 토큰을 검증하고 폐기한 뒤 회원 id 를 돌려준다. 새 토큰 발급은 호출한 쪽이 이어서 한다.
     *
     * <p>
     * 이미 폐기된 토큰이 다시 오면 그 회원의 토큰을 전부 폐기한다. 회전을 쓰는 이상
     * 한 번 쓴 토큰이 또 오는 정상적인 경로는 없다. 남은 설명은 누군가 중간에서
     * 토큰을 가져갔다는 것뿐이므로, 진짜 사용자까지 같이 끊고 다시 로그인하게 한다.
     */
    @Transactional
    public Long rotate(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken stored = refreshTokenMapper.findByTokenHash(tokenHash)
                .orElseThrow(() -> unauthorized("리프레시 토큰이 유효하지 않습니다."));

        if (stored.isRevoked()) {
            revokeAll(stored.getMemberId());
            throw unauthorized("리프레시 토큰이 유효하지 않습니다.");
        }

        if (stored.isExpired(LocalDateTime.now())) {
            throw unauthorized("리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요.");
        }

        // 위 검사와 이 UPDATE 사이에 다른 요청이 같은 토큰을 먼저 썼을 수 있다.
        // 그 경우 0행이 갱신되고, 이것도 재사용이므로 같은 처리를 한다.
        if (!refreshTokenMapper.revokeIfActive(tokenHash)) {
            revokeAll(stored.getMemberId());
            throw unauthorized("리프레시 토큰이 유효하지 않습니다.");
        }

        return stored.getMemberId();
    }

    /**
     * 로그아웃. 이미 없거나 폐기된 토큰이어도 조용히 넘어간다.
     *
     * <p>
     * 여기서 401 을 주면 "이 토큰은 존재한다"를 알려주는 셈이고, 로그아웃은 두 번 해도
     * 결과가 같아야 하는 요청이다.
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenMapper.revokeIfActive(hash(rawToken));
    }

    /**
     * 탈취가 의심되는 회원의 토큰을 전부 폐기한다.
     *
     * <p>
     * 여기까지 오는 경로에서 바깥 트랜잭션이 한 일은 {@code SELECT} 하나뿐이다.
     * InnoDB 의 일반 조회는 잠금을 잡지 않으므로, 새 트랜잭션이 같은 행을
     * {@code UPDATE} 해도 서로 기다리지 않는다.
     */
    private void revokeAll(Long memberId) {
        revokeInNewTransaction.executeWithoutResult(status -> refreshTokenMapper.revokeAllByMemberId(memberId));
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 비밀번호와 달리 BCrypt 가 아니라 SHA-256 이다. 솔트가 붙으면 매번 다른 해시가 나와
     * 조회 키로 쓸 수 없고, 난수 256비트는 사전 공격의 대상이 아니다.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 반드시 제공한다. 여기 오면 JVM 이 깨진 것이다.
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message);
    }
}
