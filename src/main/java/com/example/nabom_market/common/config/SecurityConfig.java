package com.example.nabom_market.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.nabom_market.common.security.JwtAccessDeniedHandler;
import com.example.nabom_market.common.security.JwtAuthenticationEntryPoint;
import com.example.nabom_market.common.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * JWT 기반 인증 설정.
 *
 * <p>
 * 세션을 만들지 않고({@code STATELESS}) 매 요청의 {@code Authorization: Bearer} 헤더로만
 * 인증한다. 토큰 해석은 {@link JwtAuthenticationFilter}가, 인증 실패 응답은
 * {@link JwtAuthenticationEntryPoint}가 담당한다.
 *
 * <p>
 * 상품 API는 쓰기 작업까지 전부 공개다. 관리자 역할 개념이 아직 없어서인데,
 * 역할이 도입되면 POST/PUT/DELETE는 ADMIN으로 제한해야 한다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

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
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
						.requestMatchers("/api/v1/products/**").hasRole("ADMIN")
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
						.requestMatchers("/actuator/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(e -> e
						.authenticationEntryPoint(jwtAuthenticationEntryPoint)
						.accessDeniedHandler(jwtAccessDeniedHandler))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
