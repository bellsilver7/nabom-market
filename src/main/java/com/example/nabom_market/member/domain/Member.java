package com.example.nabom_market.member.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Member {
    Long id;
    String email;
    String password;
    String name;
    LocalDateTime createdAt;

    public Member(String email, String encodedPassword, String name) {
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
    }
}
