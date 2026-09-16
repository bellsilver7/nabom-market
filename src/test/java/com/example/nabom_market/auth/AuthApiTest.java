package com.example.nabom_market.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nabom_market.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * 인증 API 명세 검증.
 *
 * <p>전제:
 * <ul>
 *   <li>샘플 회원 — id 1, liam@theres.co / password1234
 *   <li>토큰은 {@code Authorization: Bearer <token>} 헤더로 전달한다
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("인증 API")
class AuthApiTest {

    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String LOGIN = "/api/v1/auth/login";

    private static final String SAMPLE_EMAIL = "liam@theres.co";
    private static final String SAMPLE_PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 가입 테스트가 만든 회원 정리
        // (샘플 회원 1, 검증용 회원 2, 관리자 3 은 남긴다)
        jdbcTemplate.update("DELETE FROM member WHERE id > 3");
    }

    // ------------------------------------------------------------------ helpers

    private String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    /** 로그인해서 액세스 토큰을 얻는다. 다른 테스트에서도 이 방식으로 토큰을 준비한다. */
    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(body, "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("회원가입 POST /api/v1/auth/signup")
    class SignUp {

        @Test
        @DisplayName("가입하면 201과 회원 정보를 돌려준다 — 비밀번호는 응답에 없다")
        void signUpReturnsCreated() throws Exception {
            mockMvc.perform(post(SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "new@theres.co", "password": "newpassword1234", "name": "신규회원"}
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.email").value("new@theres.co"))
                    .andExpect(jsonPath("$.name").value("신규회원"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("비밀번호는 평문으로 저장되지 않는다")
        void passwordIsHashed() throws Exception {
            mockMvc.perform(post(SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "hash@theres.co", "password": "newpassword1234", "name": "해시확인"}
                            """))
                    .andExpect(status().isCreated());

            String stored = jdbcTemplate.queryForObject(
                    "SELECT password FROM member WHERE email = ?", String.class, "hash@theres.co");

            assertThat(stored).isNotEqualTo("newpassword1234");
            assertThat(stored).startsWith("$2");      // BCrypt 해시
            assertThat(stored).hasSize(60);
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 409")
        void duplicateEmailIsConflict() throws Exception {
            mockMvc.perform(post(SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "%s", "password": "whatever1234", "name": "중복"}
                            """.formatted(SAMPLE_EMAIL)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        }

        @Test
        @DisplayName("이메일 형식이 아니면 400")
        void invalidEmailIsBadRequest() throws Exception {
            mockMvc.perform(post(SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "not-an-email", "password": "newpassword1234", "name": "홍길동"}
                            """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호가 8자 미만이면 400")
        void shortPasswordIsBadRequest() throws Exception {
            mockMvc.perform(post(SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "short@theres.co", "password": "1234", "name": "홍길동"}
                            """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("로그인 POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("올바른 자격이면 액세스 토큰을 발급한다")
        void loginReturnsToken() throws Exception {
            mockMvc.perform(post(LOGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(SAMPLE_EMAIL, SAMPLE_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isString())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").isNumber());
        }

        @Test
        @DisplayName("토큰은 JWT 형식이다 — 점으로 구분된 세 부분")
        void tokenIsJwt() throws Exception {
            String token = login(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401")
        void wrongPasswordIsUnauthorized() throws Exception {
            mockMvc.perform(post(LOGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(SAMPLE_EMAIL, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("없는 이메일도 401 — 가입 여부를 알려주지 않는다")
        void unknownEmailIsUnauthorized() throws Exception {
            mockMvc.perform(post(LOGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials("nobody@theres.co", SAMPLE_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("보호된 자원 접근")
    class Protected {

        @Test
        @DisplayName("유효한 토큰이면 내 장바구니를 조회할 수 있다")
        void validTokenGrantsAccess() throws Exception {
            String token = login(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("토큰이 없으면 401")
        void noTokenIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("형식이 깨진 토큰이면 401")
        void malformedTokenIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer("not.a.jwt")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Bearer 접두사가 없으면 401")
        void missingBearerPrefixIsUnauthorized() throws Exception {
            String token = login(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            mockMvc.perform(get("/api/v1/cart").header("Authorization", token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("서명이 다른 토큰이면 401 — 페이로드만 바꿔치기할 수 없다")
        void tamperedTokenIsUnauthorized() throws Exception {
            String token = login(SAMPLE_EMAIL, SAMPLE_PASSWORD);
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + ".AAAAinvalidsignatureAAAA";

            mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(tampered)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("상품 목록은 토큰 없이도 볼 수 있다")
        void productListIsPublic() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk());
        }
    }
}
