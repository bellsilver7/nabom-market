package com.example.nabom_market.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nabom_market.TestcontainersConfiguration;
import com.example.nabom_market.common.security.JwtProvider;
import com.example.nabom_market.member.domain.Role;

/**
 * 주문 API 명세 검증.
 *
 * <p>장바구니 테스트와 마찬가지로 HTTP 계약만 검증한다.
 *
 * <p>샘플 데이터 기준:
 * <ul>
 *   <li>회원 1 (장바구니 1), 검증용 회원 2
 *   <li>상품 1 — 68,000원 / 재고 25
 *   <li>상품 2 — 18,500원 / 재고 120
 *   <li>상품 7 — 89,000원 / 재고 3 (재고 부족 시나리오용)
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("주문 API")
class OrderApiTest {

    private static final String ORDERS = "/api/v1/orders";
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

    /** 상품 가격을 바꾸려면 관리자 권한이 필요하다. (샘플 데이터의 회원 3) */
    private String adminBearer() {
        return "Bearer " + jwtProvider.createToken(3L, Role.ADMIN);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM orders");

        jdbcTemplate.update("""
                INSERT INTO member (id, email, password, name) VALUES (2, 'other@theres.co', '', '타인')
                ON DUPLICATE KEY UPDATE email = VALUES(email)
                """);

        // 클래스에 @Transactional 이 없다(= 자동 롤백이 없다).
        // 서비스의 롤백을 실제로 관찰하기 위한 선택이므로, 상태는 직접 되돌린다.
        jdbcTemplate.update("""
                UPDATE product
                   SET stock = CASE id
                       WHEN 1 THEN 25  WHEN 2 THEN 120 WHEN 3 THEN 40
                       WHEN 4 THEN 60  WHEN 5 THEN 18  WHEN 6 THEN 35
                       WHEN 7 THEN 3   WHEN 8 THEN 0   WHEN 9 THEN 200
                       WHEN 10 THEN 75 ELSE stock END,
                       price = CASE id
                       WHEN 1 THEN 68000 ELSE price END
                 WHERE id BETWEEN 1 AND 10
                """);
    }

    // ------------------------------------------------------------------ helpers

    /** 주문 요청 본문을 만든다. */
    private String orderBody(long productId, int quantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}]}
                """.formatted(productId, quantity);
    }

    /** 항목 하나짜리 주문을 만들고 생성된 주문 id를 돌려준다. */
    private Long createOrder(long memberId, long productId, int quantity) throws Exception {
        mockMvc.perform(post(ORDERS)
                .header(AUTH, bearer(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(productId, quantity)))
                .andExpect(status().isCreated());

        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE member_id = ? ORDER BY id DESC LIMIT 1", Long.class, memberId);
    }

    private int stockOf(long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("주문 생성 POST /api/v1/orders")
    class Create {

        @Test
        @DisplayName("요청한 항목으로 주문이 생성된다")
        void createsOrder() throws Exception {
            mockMvc.perform(post(ORDERS)
                    .header(AUTH, bearer(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"items": [{"productId": 1, "quantity": 2},
                                       {"productId": 2, "quantity": 3}]}
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.totalPrice").value(191500));   // 68,000x2 + 18,500x3
        }

        @Test
        @DisplayName("주문하면 그만큼 재고가 차감된다")
        void stockIsDeducted() throws Exception {
            int before = stockOf(1);
            createOrder(1, 1, 2);

            assertThat(stockOf(1)).isEqualTo(before - 2);
        }

        @Test
        @DisplayName("주문 항목은 주문 시점의 단가를 보관한다 — 이후 상품 가격이 바뀌어도 금액은 그대로")
        void orderPriceIsSnapshot() throws Exception {
            Long orderId = createOrder(1, 1, 2);

            // 상품 가격을 두 배로 인상
            mockMvc.perform(put("/api/v1/products/1")
                    .header(AUTH, adminBearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "무쇠 주물 프라이팬 24cm", "price": 136000, "stock": 25}
                            """))
                    .andExpect(status().isOk());

            mockMvc.perform(get(ORDERS + "/" + orderId).header(AUTH, bearer(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].orderPrice").value(68000))
                    .andExpect(jsonPath("$.totalPrice").value(136000));
        }

        @Test
        @DisplayName("재고가 부족하면 409 OUT_OF_STOCK")
        void outOfStockIsConflict() throws Exception {
            mockMvc.perform(post(ORDERS)
                    .header(AUTH, bearer(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderBody(7, 5)))          // 상품 7 은 재고 3
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        }

        @Test
        @DisplayName("한 항목이 재고 부족이면 다른 항목의 재고도 차감되지 않는다 — 트랜잭션 원자성")
        void allOrNothing() throws Exception {
            int stockBefore = stockOf(1);

            mockMvc.perform(post(ORDERS)
                    .header(AUTH, bearer(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"items": [{"productId": 1, "quantity": 2},
                                       {"productId": 7, "quantity": 5}]}
                            """))
                    .andExpect(status().isConflict());

            assertThat(stockOf(1))
                    .as("먼저 처리된 항목의 재고가 롤백되어야 한다")
                    .isEqualTo(stockBefore);
            assertThat(stockOf(7)).isEqualTo(3);

            Integer orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE member_id = 1", Integer.class);
            assertThat(orderCount).as("주문이 남아 있으면 안 된다").isZero();
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 404")
        void unknownProductIsNotFound() throws Exception {
            mockMvc.perform(post(ORDERS)
                    .header(AUTH, bearer(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderBody(999999, 1)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("항목이 비어 있으면 400")
        void emptyItemsIsBadRequest() throws Exception {
            mockMvc.perform(post(ORDERS)
                    .header(AUTH, bearer(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"items": []}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("수량이 0이면 400")
        void zeroQuantityIsBadRequest() throws Exception {
            mockMvc.perform(post(ORDERS)
                    .header(AUTH, bearer(1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderBody(1, 0)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("토큰이 없으면 401")
        void missingTokenIsUnauthorized() throws Exception {
            mockMvc.perform(post(ORDERS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderBody(1, 1)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("조회 GET /api/v1/orders")
    class Find {

        @Test
        @DisplayName("주문이 없으면 빈 배열")
        void emptyListWhenNoOrder() throws Exception {
            mockMvc.perform(get(ORDERS).header(AUTH, bearer(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("최신 주문이 먼저 온다")
        void listIsOrderedByNewest() throws Exception {
            Long first = createOrder(1, 1, 1);
            Long second = createOrder(1, 2, 1);

            mockMvc.perform(get(ORDERS).header(AUTH, bearer(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(second))
                    .andExpect(jsonPath("$[1].id").value(first));
        }

        @Test
        @DisplayName("상세 조회에는 주문 항목이 포함된다")
        void detailContainsItems() throws Exception {
            Long orderId = createOrder(1, 1, 2);

            mockMvc.perform(get(ORDERS + "/" + orderId).header(AUTH, bearer(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.items[0].productId").value(1))
                    .andExpect(jsonPath("$.items[0].productName").value("무쇠 주물 프라이팬 24cm"))
                    .andExpect(jsonPath("$.items[0].orderPrice").value(68000))
                    .andExpect(jsonPath("$.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.items[0].totalPrice").value(136000));
        }

        @Test
        @DisplayName("다른 회원의 주문은 목록에 보이지 않는다")
        void otherMembersOrderIsNotListed() throws Exception {
            createOrder(2, 1, 1);

            mockMvc.perform(get(ORDERS).header(AUTH, bearer(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("다른 회원의 주문 상세는 404")
        void otherMembersOrderDetailIsNotFound() throws Exception {
            Long otherOrderId = createOrder(2, 1, 1);

            mockMvc.perform(get(ORDERS + "/" + otherOrderId).header(AUTH, bearer(1)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("없는 주문은 404")
        void unknownOrderIsNotFound() throws Exception {
            mockMvc.perform(get(ORDERS + "/999999").header(AUTH, bearer(1)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("취소 POST /api/v1/orders/{id}/cancel")
    class Cancel {

        @Test
        @DisplayName("취소하면 상태가 CANCELLED 로 바뀐다")
        void cancelChangesStatus() throws Exception {
            Long orderId = createOrder(1, 1, 2);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(AUTH, bearer(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("취소하면 재고가 복구된다")
        void cancelRestoresStock() throws Exception {
            int before = stockOf(1);
            Long orderId = createOrder(1, 1, 2);
            assertThat(stockOf(1)).isEqualTo(before - 2);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(AUTH, bearer(1)))
                    .andExpect(status().isOk());

            assertThat(stockOf(1)).isEqualTo(before);
        }

        @Test
        @DisplayName("이미 취소된 주문을 다시 취소하면 409 INVALID_ORDER_STATUS")
        void cancellingTwiceIsConflict() throws Exception {
            Long orderId = createOrder(1, 1, 1);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(AUTH, bearer(1)))
                    .andExpect(status().isOk());

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(AUTH, bearer(1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
        }

        @Test
        @DisplayName("두 번 취소해도 재고가 두 번 복구되지는 않는다")
        void stockIsRestoredOnlyOnce() throws Exception {
            int before = stockOf(1);
            Long orderId = createOrder(1, 1, 2);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(AUTH, bearer(1)));
            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(AUTH, bearer(1)));

            assertThat(stockOf(1)).isEqualTo(before);
        }

        @Test
        @DisplayName("다른 회원의 주문은 취소되지 않고 404")
        void cancellingOtherMembersOrderIsNotFound() throws Exception {
            Long otherOrderId = createOrder(2, 1, 1);

            mockMvc.perform(post(ORDERS + "/" + otherOrderId + "/cancel").header(AUTH, bearer(1)))
                    .andExpect(status().isNotFound());

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, otherOrderId);
            assertThat(status).as("타인의 주문이 취소되면 안 된다").isEqualTo("PENDING");
        }

        @Test
        @DisplayName("없는 주문을 취소하면 404")
        void cancellingUnknownOrderIsNotFound() throws Exception {
            mockMvc.perform(post(ORDERS + "/999999/cancel").header(AUTH, bearer(1)))
                    .andExpect(status().isNotFound());
        }
    }
}
