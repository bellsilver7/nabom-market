package com.example.nabom_market.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nabom_market.auth.dto.LoginRequest;
import com.example.nabom_market.auth.dto.MemberResponse;
import com.example.nabom_market.auth.dto.RefreshTokenRequest;
import com.example.nabom_market.auth.dto.SignUpRequest;
import com.example.nabom_market.auth.dto.TokenResponse;
import com.example.nabom_market.common.exception.BusinessException;
import com.example.nabom_market.common.exception.ErrorCode;
import com.example.nabom_market.common.security.JwtProvider;
import com.example.nabom_market.member.domain.Member;
import com.example.nabom_market.member.mapper.MemberMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public MemberResponse signUp(SignUpRequest request) {
        if (memberMapper.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 가입된 이메일입니다. " + request.email());
        }

        Member member = new Member(request.email(), passwordEncoder.encode(request.password()), request.name());

        memberMapper.insert(member);

        return MemberResponse.from(member);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member = memberMapper.findByEmail(request.email())
                .filter(m -> passwordEncoder.matches(request.password(), m.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        return issueTokens(member);
    }

    /**
     * 액세스 토큰 재발급. 쓴 리프레시 토큰은 폐기되고 새 토큰이 함께 나간다.
     *
     * <p>
     * 회원을 다시 읽는 이유는 역할 때문이다. 토큰을 발급한 뒤 관리자 권한이 회수됐다면
     * 재발급 시점에 반영되어야 한다. 리프레시 토큰에 역할을 넣어 뒀다면 회수된 권한이
     * 만료일까지 살아남는다.
     */
    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        Long memberId = refreshTokenService.rotate(request.refreshToken());

        Member member = memberMapper.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."));

        return issueTokens(member);
    }

    /**
     * 로그아웃 — 리프레시 토큰을 폐기한다.
     *
     * <p>
     * 이미 발급된 액세스 토큰은 회수하지 못한다. 서명만 맞으면 통과하는 물건이라
     * 남은 만료 시간(최대 {@code jwt.expiration-minutes})까지는 그대로 동작한다.
     * 재발급 경로가 끊기므로 그 시간이 지나면 다시 로그인해야 한다.
     */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse issueTokens(Member member) {
        return TokenResponse.of(
                jwtProvider.createToken(member.getId(), member.getRole()),
                refreshTokenService.issue(member.getId()),
                jwtProvider.getExpiresInSeconds());
    }
}
