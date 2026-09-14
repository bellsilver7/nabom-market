package com.example.nabom_market.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nabom_market.auth.dto.MemberResponse;
import com.example.nabom_market.auth.dto.SignUpRequest;
import com.example.nabom_market.common.exception.BusinessException;
import com.example.nabom_market.common.exception.ErrorCode;
import com.example.nabom_market.member.domain.Member;
import com.example.nabom_market.member.mapper.MemberMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse signUp(SignUpRequest request) {
        if (memberMapper.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 가입된 이메일입니다. " + request.email());
        }

        Member member = new Member(request.email(), passwordEncoder.encode(request.password()), request.name());

        memberMapper.insert(member);

        return MemberResponse.from(member);
    }
}
