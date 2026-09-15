package com.example.nabom_market.common.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.nabom_market.common.security.LoginMember;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI 설정.
 *
 * <p>
 * springdoc 은 컨트롤러 시그니처를 읽어 문서를 만들지만, 인증 방식은 알지 못한다.
 * JWT 검증이 Security 필터에서 일어나기 때문에 컨트롤러 어디에도 흔적이 없어서다.
 * 그래서 Bearer 스킴을 직접 등록해 준다. 이게 있어야 Swagger UI 에 Authorize 버튼이 생기고,
 * 한 번 토큰을 넣으면 이후 요청에 Authorization 헤더가 자동으로 붙는다.
 *
 * <p>
 * {@code @LoginMember} 도 같은 이유로 알려줘야 한다. springdoc 은 컨트롤러 시그니처만 보므로
 * 이 파라미터를 클라이언트가 채워 넣는 값으로 오해하고 문서에 입력란을 만든다.
 * 실제로는 토큰에서 꺼내는 값이라 요청에 담기지 않는다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    static {
        // @LoginMember 가 붙은 파라미터는 문서에서 제외한다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginMember.class);
    }

    @Bean
    OpenAPI nabomOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인 응답의 accessToken 을 그대로 붙여넣는다. 'Bearer ' 접두사는 UI 가 붙인다.");

        return new OpenAPI()
                .info(new Info()
                        .title("nabom API")
                        .description("작고 단단한 쇼핑몰 백엔드 API")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
