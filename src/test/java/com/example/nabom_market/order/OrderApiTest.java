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
import org.springframework.transaction.annotation.Transactional;

import com.example.nabom_market.TestcontainersConfiguration;

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
@Transactional
@DisplayName("주문 API")
class OrderApiTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final String CART_ITEMS = "/api/v1/cart/items";
    private static final String MEMBER = "X-MEMBER-ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM cart_item");

        jdbcTemplate.update("""
                INSERT INTO member (id, email, name) VALUES (2, 'other@theres.co', '타인')
                ON DUPLICATE KEY UPDATE email = VALUES(email)
                """);
        jdbcTemplate.update("""
                INSERT INTO cart (id, member_id) VALUES (2, 2)
                ON DUPLICATE KEY UPDATE member_id = VALUES(member_id)
                """);
    }

    // ------------------------------------------------------------------ helpers

    private void addToCart(long memberId, long productId, int quantity) throws Exception {
        mockMvc.perform(post(CART_ITEMS)
                .header(MEMBER, memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"productId": %d, "quantity": %d}
                        """.formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    /** 주문을 생성하고 생성된 주문 id를 돌려준다. */
    private Long placeOrder(long memberId) throws Exception {
        mockMvc.perform(post(ORDERS).header(MEMBER, memberId))
                .andExpect(status().isCreated());

        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE member_id = ? ORDER BY id DESC LIMIT 1", Long.class, memberId);
    }

    private int stockOf(long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    private int cartItemCount(long memberId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cart_item ci
                  JOIN cart c ON c.id = ci.cart_id
                 WHERE c.member_id = ?
                """, Integer.class, memberId);
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("주문 생성 POST /api/v1/orders")
    class Place {

        @Test
        @DisplayName("장바구니의 상품으로 주문이 생성된다")
        void createsOrderFromCart() throws Exception {
            addToCart(1, 1, 2);   // 68,000 x 2 = 136,000
            addToCart(1, 2, 3);   // 18,500 x 3 =  55,500

            mockMvc.perform(post(ORDERS).header(MEMBER, 1))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.totalPrice").value(191500));
        }

        @Test
        @DisplayName("주문하면 장바구니가 비워진다")
        void cartIsClearedAfterOrder() throws Exception {
            addToCart(1, 1, 2);
            placeOrder(1);

            assertThat(cartItemCount(1)).isZero();
        }

        @Test
        @DisplayName("주문하면 그만큼 재고가 차감된다")
        void stockIsDeducted() throws Exception {
            int before = stockOf(1);
            addToCart(1, 1, 2);
            placeOrder(1);

            assertThat(stockOf(1)).isEqualTo(before - 2);
        }

        @Test
        @DisplayName("주문 항목은 주문 시점의 단가를 보관한다 — 이후 상품 가격이 바뀌어도 금액은 그대로")
        void orderPriceIsSnapshot() throws Exception {
            addToCart(1, 1, 2);
            Long orderId = placeOrder(1);

            // 상품 가격을 두 배로 인상
            mockMvc.perform(put("/api/v1/products/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "무쇠 주물 프라이팬 24cm", "price": 136000, "stock": 25}
                            """))
                    .andExpect(status().isOk());

            mockMvc.perform(get(ORDERS + "/" + orderId).header(MEMBER, 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].orderPrice").value(68000))
                    .andExpect(jsonPath("$.totalPrice").value(136000));
        }

        @Test
        @DisplayName("재고가 부족하면 409 OUT_OF_STOCK")
        void outOfStockIsConflict() throws Exception {
            addToCart(1, 7, 5);   // 상품 7 은 재고 3

            mockMvc.perform(post(ORDERS).header(MEMBER, 1))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        }

        @Test
        @DisplayName("한 항목이 재고 부족이면 다른 항목의 재고도 차감되지 않는다 — 트랜잭션 원자성")
        void allOrNothing() throws Exception {
            int stockBefore = stockOf(1);

            addToCart(1, 1, 2);   // 재고 충분
            addToCart(1, 7, 5);   // 재고 부족 (3개뿐)

            mockMvc.perform(post(ORDERS).header(MEMBER, 1))
                    .andExpect(status().isConflict());

            assertThat(stockOf(1))
                    .as("먼저 처리된 항목의 재고가 롤백되어야 한다")
                    .isEqualTo(stockBefore);
            assertThat(stockOf(7)).isEqualTo(3);

            Integer orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE member_id = 1", Integer.class);
            assertThat(orderCount).as("주문이 남아 있으면 안 된다").isZero();

            assertThat(cartItemCount(1)).as("장바구니도 비워지면 안 된다").isEqualTo(2);
        }

        @Test
        @DisplayName("장바구니가 비어 있으면 400")
        void emptyCartIsBadRequest() throws Exception {
            mockMvc.perform(post(ORDERS).header(MEMBER, 1))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400")
        void missingMemberHeaderIsBadRequest() throws Exception {
            mockMvc.perform(post(ORDERS))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("조회 GET /api/v1/orders")
    class Find {

        @Test
        @DisplayName("주문이 없으면 빈 배열")
        void emptyListWhenNoOrder() throws Exception {
            mockMvc.perform(get(ORDERS).header(MEMBER, 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("최신 주문이 먼저 온다")
        void listIsOrderedByNewest() throws Exception {
            addToCart(1, 1, 1);
            Long first = placeOrder(1);
            addToCart(1, 2, 1);
            Long second = placeOrder(1);

            mockMvc.perform(get(ORDERS).header(MEMBER, 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(second))
                    .andExpect(jsonPath("$[1].id").value(first));
        }

        @Test
        @DisplayName("상세 조회에는 주문 항목이 포함된다")
        void detailContainsItems() throws Exception {
            addToCart(1, 1, 2);
            Long orderId = placeOrder(1);

            mockMvc.perform(get(ORDERS + "/" + orderId).header(MEMBER, 1))
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
            addToCart(2, 1, 1);
            placeOrder(2);

            mockMvc.perform(get(ORDERS).header(MEMBER, 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("다른 회원의 주문 상세는 404")
        void otherMembersOrderDetailIsNotFound() throws Exception {
            addToCart(2, 1, 1);
            Long otherOrderId = placeOrder(2);

            mockMvc.perform(get(ORDERS + "/" + otherOrderId).header(MEMBER, 1))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("없는 주문은 404")
        void unknownOrderIsNotFound() throws Exception {
            mockMvc.perform(get(ORDERS + "/999999").header(MEMBER, 1))
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
            addToCart(1, 1, 2);
            Long orderId = placeOrder(1);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(MEMBER, 1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("취소하면 재고가 복구된다")
        void cancelRestoresStock() throws Exception {
            int before = stockOf(1);
            addToCart(1, 1, 2);
            Long orderId = placeOrder(1);
            assertThat(stockOf(1)).isEqualTo(before - 2);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(MEMBER, 1))
                    .andExpect(status().isOk());

            assertThat(stockOf(1)).isEqualTo(before);
        }

        @Test
        @DisplayName("이미 취소된 주문을 다시 취소하면 409 INVALID_ORDER_STATUS")
        void cancellingTwiceIsConflict() throws Exception {
            addToCart(1, 1, 1);
            Long orderId = placeOrder(1);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(MEMBER, 1))
                    .andExpect(status().isOk());

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(MEMBER, 1))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
        }

        @Test
        @DisplayName("두 번 취소해도 재고가 두 번 복구되지는 않는다")
        void stockIsRestoredOnlyOnce() throws Exception {
            int before = stockOf(1);
            addToCart(1, 1, 2);
            Long orderId = placeOrder(1);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(MEMBER, 1));
            mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(MEMBER, 1));

            assertThat(stockOf(1)).isEqualTo(before);
        }

        @Test
        @DisplayName("다른 회원의 주문은 취소되지 않고 404")
        void cancellingOtherMembersOrderIsNotFound() throws Exception {
            addToCart(2, 1, 1);
            Long otherOrderId = placeOrder(2);

            mockMvc.perform(post(ORDERS + "/" + otherOrderId + "/cancel").header(MEMBER, 1))
                    .andExpect(status().isNotFound());

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, otherOrderId);
            assertThat(status).as("타인의 주문이 취소되면 안 된다").isEqualTo("PENDING");
        }

        @Test
        @DisplayName("없는 주문을 취소하면 404")
        void cancellingUnknownOrderIsNotFound() throws Exception {
            mockMvc.perform(post(ORDERS + "/999999/cancel").header(MEMBER, 1))
                    .andExpect(status().isNotFound());
        }
    }
}
