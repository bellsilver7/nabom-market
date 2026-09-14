package com.example.nabom_market.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증 도입 전 임시 설정.
 *
 * <p>
 * Spring Security가 클래스패스에 있으면 기본적으로 모든 엔드포인트가 잠기고
 * 콘솔에 랜덤 비밀번호가 출력된다. 현재는 {@code X-MEMBER-ID} 헤더로 사용자를 식별하므로
 * 모든 요청을 허용해 둔다.
 *
 * <p>
 * TODO: JWT 인증 도입 시 이 설정을 교체할 것.
 */
@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.build();
	}
}
