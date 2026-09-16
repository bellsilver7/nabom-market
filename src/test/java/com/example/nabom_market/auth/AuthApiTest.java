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
import org.springframework.test.web.servlet.ResultActions;

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
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    private static final String SAMPLE_EMAIL = "liam@theres.co";
    private static final String SAMPLE_PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 앞선 테스트가 발급한 리프레시 토큰 정리.
        // 재사용 탐지가 회원의 토큰을 전부 폐기하므로, 남겨 두면 테스트끼리 간섭한다.
        jdbcTemplate.update("DELETE FROM refresh_token");

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

    /** 로그인 응답 본문. 액세스 토큰과 리프레시 토큰이 함께 들어 있다. */
    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** 로그인해서 액세스 토큰을 얻는다. 다른 테스트에서도 이 방식으로 토큰을 준비한다. */
    private String login(String email, String password) throws Exception {
        return JsonPath.read(loginBody(email, password), "$.accessToken");
    }

    private String loginRefreshToken(String email, String password) throws Exception {
        return JsonPath.read(loginBody(email, password), "$.refreshToken");
    }

    private String tokenBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tokenBody(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post(LOGOUT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tokenBody(refreshToken)));
    }

    /** 재발급을 한 번 하고 새로 받은 리프레시 토큰을 돌려준다. */
    private String rotate(String refreshToken) throws Exception {
        String body = refresh(refreshToken)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(body, "$.refreshToken");
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
                    .andExpect(jsonPath("$.refreshToken").isString())
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
    @DisplayName("재발급 POST /api/v1/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("재발급하면 액세스 토큰과 리프레시 토큰을 새로 준다")
        void refreshIssuesNewPair() throws Exception {
            String body = loginBody(SAMPLE_EMAIL, SAMPLE_PASSWORD);
            String oldAccess = JsonPath.read(body, "$.accessToken");
            String oldRefresh = JsonPath.read(body, "$.refreshToken");

            String refreshed = refresh(oldRefresh)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat((String) JsonPath.read(refreshed, "$.refreshToken")).isNotEqualTo(oldRefresh);
            assertThat((String) JsonPath.read(refreshed, "$.accessToken")).isNotEmpty();
            // 액세스 토큰은 같은 초에 발급되면 페이로드가 같아 값도 같을 수 있다.
            // 그래서 여기서는 비교하지 않고, 아래 테스트에서 "쓸 수 있는지"로 확인한다.
            assertThat(oldAccess).isNotEmpty();
        }

        @Test
        @DisplayName("재발급받은 액세스 토큰으로 보호된 자원에 접근할 수 있다")
        void refreshedAccessTokenWorks() throws Exception {
            String refreshToken = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            String refreshed = refresh(refreshToken)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            mockMvc.perform(get("/api/v1/cart")
                    .header("Authorization", bearer(JsonPath.read(refreshed, "$.accessToken"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("한 번 쓴 리프레시 토큰은 다시 쓸 수 없다")
        void usedTokenIsRejected() throws Exception {
            String refreshToken = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);
            rotate(refreshToken);

            refresh(refreshToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("재사용이 감지되면 그 회원의 리프레시 토큰이 전부 폐기된다")
        void reuseRevokesEveryToken() throws Exception {
            String first = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);
            String second = rotate(first);

            // 탈취된 옛 토큰이 다시 온 상황
            refresh(first).andExpect(status().isUnauthorized());

            // 진짜 사용자가 들고 있던 새 토큰도 함께 끊긴다 — 다시 로그인해야 한다
            refresh(second).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("만료된 리프레시 토큰이면 401")
        void expiredTokenIsUnauthorized() throws Exception {
            String refreshToken = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            jdbcTemplate.update(
                    "UPDATE refresh_token SET expires_at = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE revoked_at IS NULL");

            refresh(refreshToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("없는 토큰이면 401 — 폐기된 토큰과 구분해 주지 않는다")
        void unknownTokenIsUnauthorized() throws Exception {
            refresh("this-token-was-never-issued")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("빈 토큰이면 400")
        void blankTokenIsBadRequest() throws Exception {
            refresh("").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("리프레시 토큰 원문은 저장되지 않는다 — SHA-256 해시만 남는다")
        void rawTokenIsNotStored() throws Exception {
            String refreshToken = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            Integer stored = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ?", Integer.class, refreshToken);
            assertThat(stored).isZero();

            String hash = jdbcTemplate.queryForObject(
                    "SELECT token_hash FROM refresh_token ORDER BY id DESC LIMIT 1", String.class);
            assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("로그아웃 POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("로그아웃하면 204")
        void logoutReturnsNoContent() throws Exception {
            String refreshToken = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            logout(refreshToken).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("로그아웃한 리프레시 토큰으로는 재발급할 수 없다")
        void logoutBlocksRefresh() throws Exception {
            String refreshToken = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            logout(refreshToken).andExpect(status().isNoContent());

            refresh(refreshToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("두 번 로그아웃해도 204 — 몇 번을 해도 결과가 같다")
        void logoutIsIdempotent() throws Exception {
            String refreshToken = loginRefreshToken(SAMPLE_EMAIL, SAMPLE_PASSWORD);

            logout(refreshToken).andExpect(status().isNoContent());
            logout(refreshToken).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("없는 토큰으로 로그아웃해도 204 — 존재 여부를 알려주지 않는다")
        void logoutWithUnknownTokenIsNoContent() throws Exception {
            logout("this-token-was-never-issued").andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("로그아웃해도 이미 발급된 액세스 토큰은 만료 전까지 유효하다 — 알려진 한계")
        void accessTokenSurvivesLogout() throws Exception {
            String body = loginBody(SAMPLE_EMAIL, SAMPLE_PASSWORD);
            String accessToken = JsonPath.read(body, "$.accessToken");
            String refreshToken = JsonPath.read(body, "$.refreshToken");

            logout(refreshToken).andExpect(status().isNoContent());

            // 서명만 맞으면 통과하는 물건이라 서버가 회수할 방법이 없다.
            // 재발급 경로가 끊겼으므로 만료되는 순간 다시 로그인해야 한다.
            mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(accessToken)))
                    .andExpect(status().isOk());

            refresh(refreshToken).andExpect(status().isUnauthorized());
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
