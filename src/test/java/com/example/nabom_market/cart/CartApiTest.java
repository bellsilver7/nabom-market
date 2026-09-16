package com.example.nabom_market.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.transaction.annotation.Transactional;

import com.example.nabom_market.TestcontainersConfiguration;
import com.example.nabom_market.common.security.JwtProvider;
import com.example.nabom_market.member.domain.Role;

/**
 * 장바구니 API 명세 검증.
 *
 * <p>
 * 내부 클래스 구조가 아니라 HTTP 계약(경로, 상태 코드, 응답 JSON)만 검증한다.
 * 따라서 서비스/매퍼를 어떻게 리팩터링하든 이 테스트는 그대로 유효하다.
 *
 * <p>
 * 전제:
 * <ul>
 * <li>샘플 데이터의 회원 id=1, 장바구니 id=1
 * <li>상품 id=1 (재고 25), id=2 (재고 120), id=7 (재고 3), id=8 (재고 0)
 * <li>인증은 {@code Authorization: Bearer <token>} 헤더
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("장바구니 API")
class CartApiTest {

        private static final String CART = "/api/v1/cart";
        private static final String ITEMS = "/api/v1/cart/items";
        private static final String AUTH = "Authorization";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private JwtProvider jwtProvider;

        /** 해당 회원으로 인증된 Authorization 헤더 값을 만든다. */
        private String bearer(long memberId) {
                return "Bearer " + jwtProvider.createToken(memberId, Role.USER);
        }

        @BeforeEach
        void setUp() {
                // 각 테스트는 빈 장바구니에서 시작한다
                jdbcTemplate.update("DELETE FROM cart_item");

                // 타인 소유 데이터 검증용 회원 2와 그의 장바구니
                jdbcTemplate.update("""
                                INSERT INTO member (id, email, password, name) VALUES (2, 'other@theres.co', '', '타인')
                                ON DUPLICATE KEY UPDATE email = VALUES(email)
                                """);
                jdbcTemplate.update("""
                                INSERT INTO cart (id, member_id) VALUES (2, 2)
                                ON DUPLICATE KEY UPDATE member_id = VALUES(member_id)
                                """);
        }

        private String addItemBody(long productId, int quantity) {
                return """
                                {"productId": %d, "quantity": %d}
                                """.formatted(productId, quantity);
        }

        /** 상품을 담고 생성된 cart_item id를 돌려준다. */
        private Long addItem(long productId, int quantity) throws Exception {
                mockMvc.perform(post(ITEMS)
                                .header(AUTH, bearer(1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(addItemBody(productId, quantity)))
                                .andExpect(status().isOk());

                return jdbcTemplate.queryForObject(
                                "SELECT id FROM cart_item WHERE cart_id = 1 AND product_id = ?", Long.class, productId);
        }

        // ------------------------------------------------------------------
        @Nested
        @DisplayName("조회 GET /api/v1/cart")
        class Find {

                @Test
                @DisplayName("비어 있어도 404가 아니라 빈 배열을 담은 200을 준다")
                void emptyCartReturnsOk() throws Exception {
                        mockMvc.perform(get(CART).header(AUTH, bearer(1)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.items").isArray())
                                        .andExpect(jsonPath("$.items.length()").value(0))
                                        .andExpect(jsonPath("$.totalPrice").value(0));
                }

                @Test
                @DisplayName("항목에 상품명·단가·소계(totalPrice)가 함께 내려온다")
                void itemContainsProductInfo() throws Exception {
                        addItem(1, 2); // 무쇠 주물 프라이팬 24cm, 68,000원

                        mockMvc.perform(get(CART).header(AUTH, bearer(1)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.items[0].id").isNumber())
                                        .andExpect(jsonPath("$.items[0].productId").value(1))
                                        .andExpect(jsonPath("$.items[0].productName").value("무쇠 주물 프라이팬 24cm"))
                                        .andExpect(jsonPath("$.items[0].productPrice").value(68000))
                                        .andExpect(jsonPath("$.items[0].quantity").value(2))
                                        .andExpect(jsonPath("$.items[0].totalPrice").value(136000));
                }

                @Test
                @DisplayName("총액을 서버가 계산해 내려준다")
                void totalsAreCalculatedByServer() throws Exception {
                        addItem(1, 2); // 68,000 x 2 = 136,000
                        addItem(2, 3); // 18,500 x 3 = 55,500

                        mockMvc.perform(get(CART).header(AUTH, bearer(1)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.totalPrice").value(191500));
                }

                @Test
                @DisplayName("품절 상품은 available=false 로 표시된다")
                void soldOutItemIsMarkedUnavailable() throws Exception {
                        addItem(8, 1); // 재고 0

                        mockMvc.perform(get(CART).header(AUTH, bearer(1)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.items[0].available").value(false));
                }

                @Test
                @DisplayName("토큰이 없으면 401")
                void missingTokenIsUnauthorized() throws Exception {
                        mockMvc.perform(get(CART))
                                        .andExpect(status().isUnauthorized());
                }

                @Test
                @DisplayName("다른 회원의 항목은 내 장바구니에 보이지 않는다")
                void otherMembersItemsAreNotVisible() throws Exception {
                        jdbcTemplate.update("INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (2, 1, 9)");

                        mockMvc.perform(get(CART).header(AUTH, bearer(1)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.items.length()").value(0));
                }
        }

        // ------------------------------------------------------------------
        @Nested
        @DisplayName("담기 POST /api/v1/cart/items")
        class Add {

                @Test
                @DisplayName("담으면 갱신된 장바구니 전체가 응답으로 온다")
                void addReturnsUpdatedCart() throws Exception {
                        mockMvc.perform(post(ITEMS)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(addItemBody(1, 2)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.items.length()").value(1))
                                        .andExpect(jsonPath("$.items[0].quantity").value(2))
                                        .andExpect(jsonPath("$.totalPrice").value(136000));
                }

                @Test
                @DisplayName("같은 상품을 두 번 담으면 행이 늘지 않고 수량만 합산된다")
                void addingSameProductTwiceMergesQuantity() throws Exception {
                        addItem(1, 2);
                        addItem(1, 3);

                        mockMvc.perform(get(CART).header(AUTH, bearer(1)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.items.length()").value(1))
                                        .andExpect(jsonPath("$.items[0].quantity").value(5));

                        Integer rows = jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM cart_item WHERE cart_id = 1 AND product_id = 1",
                                        Integer.class);
                        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);
                }

                @Test
                @DisplayName("존재하지 않는 상품이면 404 NOT_FOUND")
                void addingUnknownProductIsNotFound() throws Exception {
                        mockMvc.perform(post(ITEMS)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(addItemBody(999999, 1)))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
                }

                @Test
                @DisplayName("수량이 0이면 400 INVALID_REQUEST")
                void zeroQuantityIsBadRequest() throws Exception {
                        mockMvc.perform(post(ITEMS)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(addItemBody(1, 0)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
                }

                @Test
                @DisplayName("수량이 음수면 400")
                void negativeQuantityIsBadRequest() throws Exception {
                        mockMvc.perform(post(ITEMS)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(addItemBody(1, -1)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("productId가 빠지면 400")
                void missingProductIdIsBadRequest() throws Exception {
                        mockMvc.perform(post(ITEMS)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {"quantity": 1}
                                                        """))
                                        .andExpect(status().isBadRequest());
                }
        }

        // ------------------------------------------------------------------
        @Nested
        @DisplayName("수량 변경 PATCH /api/v1/cart/items/{itemId}")
        class UpdateQuantity {

                @Test
                @DisplayName("수량을 바꾸면 소계와 총액이 함께 갱신된다")
                void quantityIsUpdated() throws Exception {
                        Long itemId = addItem(1, 2);

                        mockMvc.perform(patch(ITEMS + "/" + itemId)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {"quantity": 5}
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.items[0].quantity").value(5))
                                        .andExpect(jsonPath("$.items[0].totalPrice").value(340000))
                                        .andExpect(jsonPath("$.totalPrice").value(340000));
                }

                @Test
                @DisplayName("수량 0은 400 — 삭제는 DELETE를 쓴다")
                void zeroQuantityIsBadRequest() throws Exception {
                        Long itemId = addItem(1, 2);

                        mockMvc.perform(patch(ITEMS + "/" + itemId)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {"quantity": 0}
                                                        """))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("없는 itemId면 404")
                void unknownItemIsNotFound() throws Exception {
                        mockMvc.perform(patch(ITEMS + "/999999")
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {"quantity": 3}
                                                        """))
                                        .andExpect(status().isNotFound());
                }

                @Test
                @DisplayName("다른 회원의 항목은 403이 아니라 404 — 존재 여부를 숨긴다")
                void otherMembersItemIsNotFound() throws Exception {
                        jdbcTemplate.update("INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (2, 1, 1)");
                        Long otherItemId = jdbcTemplate.queryForObject(
                                        "SELECT id FROM cart_item WHERE cart_id = 2", Long.class);

                        mockMvc.perform(patch(ITEMS + "/" + otherItemId)
                                        .header(AUTH, bearer(1))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {"quantity": 3}
                                                        """))
                                        .andExpect(status().isNotFound());

                        Integer quantity = jdbcTemplate.queryForObject(
                                        "SELECT quantity FROM cart_item WHERE id = ?", Integer.class, otherItemId);
                        org.assertj.core.api.Assertions.assertThat(quantity)
                                        .as("타인의 항목이 변경되면 안 된다")
                                        .isEqualTo(1);
                }
        }

        // ------------------------------------------------------------------
        @Nested
        @DisplayName("삭제 DELETE /api/v1/cart/items/{itemId}")
        class Remove {

                @Test
                @DisplayName("삭제하면 204, 목록에서 사라진다")
                void removeReturnsNoContent() throws Exception {
                        Long itemId = addItem(1, 2);

                        mockMvc.perform(delete(ITEMS + "/" + itemId).header(AUTH, bearer(1)))
                                        .andExpect(status().isNoContent());

                        mockMvc.perform(get(CART).header(AUTH, bearer(1)))
                                        .andExpect(jsonPath("$.items.length()").value(0));
                }

                @Test
                @DisplayName("없는 itemId면 404 — 500이 아니다")
                void unknownItemIsNotFound() throws Exception {
                        mockMvc.perform(delete(ITEMS + "/999999").header(AUTH, bearer(1)))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
                }

                @Test
                @DisplayName("다른 회원의 항목은 삭제되지 않고 404")
                void otherMembersItemIsNotFound() throws Exception {
                        jdbcTemplate.update("INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (2, 1, 1)");
                        Long otherItemId = jdbcTemplate.queryForObject(
                                        "SELECT id FROM cart_item WHERE cart_id = 2", Long.class);

                        mockMvc.perform(delete(ITEMS + "/" + otherItemId).header(AUTH, bearer(1)))
                                        .andExpect(status().isNotFound());

                        Integer remaining = jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM cart_item WHERE id = ?", Integer.class, otherItemId);
                        org.assertj.core.api.Assertions.assertThat(remaining)
                                        .as("타인의 항목이 삭제되면 안 된다")
                                        .isEqualTo(1);
                }
        }
}
