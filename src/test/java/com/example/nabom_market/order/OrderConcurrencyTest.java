package com.example.nabom_market.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.nabom_market.TestcontainersConfiguration;
import com.example.nabom_market.order.dto.OrderCreateRequest;
import com.example.nabom_market.order.dto.OrderItemCreateRequest;
import com.example.nabom_market.order.dto.OrderResponse;
import com.example.nabom_market.order.service.OrderService;

/**
 * 동시 주문 시 재고 정합성 검증.
 *
 * <p>
 * README 의 주장 — "검사와 차감을 한 문장으로 합치면 InnoDB 의 행 잠금만으로
 * 경쟁 조건이 해소된다" — 을 실제로 증명한다.
 *
 * <pre>
 * UPDATE product SET stock = stock - #{quantity}
 *  WHERE id = #{productId} AND stock &gt;= #{quantity}
 * </pre>
 *
 * <p>
 * 설계상 주의할 점 둘:
 *
 * <ul>
 * <li>클래스에 {@code @Transactional} 을 붙이지 않는다. 붙이면 모든 스레드가
 * 테스트의 트랜잭션에 합류하지 않고 각자 별도 커넥션을 쓰게 되는데,
 * 정작 테스트가 만든 픽스처는 커밋되지 않아 다른 스레드에서 보이지 않는다.
 * <li>MockMvc 대신 서비스를 직접 호출한다. 검증 대상은 DB 수준의 정합성이고,
 * 서블릿 계층을 거친다고 증거가 더 늘지 않는다.
 * </ul>
 *
 * <p>
 * 커넥션 풀 기본값이 10 이므로 스레드 수는 30 을 넘기지 않는다.
 * 남는 스레드는 커넥션을 기다릴 뿐이고, 경합 자체는 그대로 일어난다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("동시 주문 재고 정합성")
class OrderConcurrencyTest {

    /** 샘플 데이터와 섞이지 않도록 전용 id 대역을 쓴다. */
    private static final long PLENTIFUL = 9001L; // 재고가 넉넉한 상품
    private static final long SCARCE = 9002L; // 재고가 빠듯한 상품

    private static final long MEMBER_ID = 1L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        // 이 테스트는 롤백되지 않는다. 남기면 다른 테스트의 상품 개수 단언이 깨진다.
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM product WHERE id >= 9000");
    }

    // ------------------------------------------------------------------ helpers

    private void givenProduct(long id, int price, int stock) {
        jdbcTemplate.update(
                "INSERT INTO product (id, name, price, stock) VALUES (?, ?, ?, ?)",
                id, "동시성 테스트 상품 " + id, price, stock);
    }

    private int stockOf(long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    private long orderCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
    }

    private OrderCreateRequest orderOf(OrderItemCreateRequest... items) {
        return new OrderCreateRequest(List.of(items));
    }

    private OrderItemCreateRequest item(long productId, int quantity) {
        return new OrderItemCreateRequest(productId, quantity);
    }

    /** 성공 건수와 실패 건수. */
    private record Outcome(int success, int failure) {
    }

    /**
     * 주어진 작업을 스레드 수만큼 동시에 실행한다.
     *
     * <p>
     * 모든 스레드를 먼저 대기시켰다가 한 번에 출발시킨다. 그냥 submit 만 하면
     * 앞선 스레드가 이미 끝난 뒤에 뒤 스레드가 시작해 경합이 일어나지 않을 수 있다.
     */
    private Outcome runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();

                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        done.countDown();
                        return;
                    }

                    try {
                        task.run();
                        success.incrementAndGet();
                    } catch (RuntimeException e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("모든 스레드가 준비됨").isTrue();
            start.countDown();
            assertThat(done.await(120, TimeUnit.SECONDS)).as("모든 스레드가 종료됨").isTrue();
        } finally {
            pool.shutdownNow();
        }

        return new Outcome(success.get(), failure.get());
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("재고 차감")
    class Deduction {

        @Test
        @DisplayName("재고보다 많은 주문이 몰려도 딱 재고만큼만 성공한다")
        void onlyAsManyAsStockSucceed() throws Exception {
            givenProduct(SCARCE, 10_000, 10);

            Outcome outcome = runConcurrently(30,
                    () -> orderService.create(MEMBER_ID, orderOf(item(SCARCE, 1))));

            assertThat(outcome.success()).isEqualTo(10);
            assertThat(outcome.failure()).isEqualTo(20);
            assertThat(stockOf(SCARCE)).isZero();
        }

        @Test
        @DisplayName("재고는 음수가 되지 않는다")
        void stockNeverGoesNegative() throws Exception {
            givenProduct(SCARCE, 10_000, 7);

            runConcurrently(30, () -> orderService.create(MEMBER_ID, orderOf(item(SCARCE, 1))));

            assertThat(stockOf(SCARCE)).isNotNegative();
        }

        @Test
        @DisplayName("수량이 1보다 커도 성공 건수와 차감량이 맞아떨어진다")
        void deductedAmountMatchesSuccessCount() throws Exception {
            givenProduct(SCARCE, 10_000, 24);

            Outcome outcome = runConcurrently(30,
                    () -> orderService.create(MEMBER_ID, orderOf(item(SCARCE, 3))));

            assertThat(outcome.success()).isEqualTo(8); // 24 / 3
            assertThat(stockOf(SCARCE)).isZero();
        }

        @Test
        @DisplayName("실패한 주문은 흔적을 남기지 않는다")
        void failedOrdersLeaveNothingBehind() throws Exception {
            givenProduct(SCARCE, 10_000, 10);

            Outcome outcome = runConcurrently(30,
                    () -> orderService.create(MEMBER_ID, orderOf(item(SCARCE, 1))));

            assertThat(orderCount()).isEqualTo(outcome.success());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("부분 실패 롤백")
    class Rollback {

        @Test
        @DisplayName("뒤 항목이 품절이면 앞 항목의 차감도 되돌린다")
        void rollsBackEarlierDeductions() throws Exception {
            givenProduct(PLENTIFUL, 1_000, 1_000);
            givenProduct(SCARCE, 10_000, 5);

            // PLENTIFUL 을 먼저 차감한 뒤 SCARCE 에서 막히는 순서
            Outcome outcome = runConcurrently(30,
                    () -> orderService.create(MEMBER_ID,
                            orderOf(item(PLENTIFUL, 1), item(SCARCE, 1))));

            assertThat(outcome.success()).isEqualTo(5);
            assertThat(stockOf(SCARCE)).isZero();

            // 실패한 25건이 PLENTIFUL 을 건드렸다면 975 가 된다
            assertThat(stockOf(PLENTIFUL)).isEqualTo(995);
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("동시 취소")
    class Cancellation {

        @Test
        @DisplayName("같은 주문을 동시에 취소해도 재고는 한 번만 복구된다")
        void cancelRestoresStockOnlyOnce() throws Exception {
            givenProduct(SCARCE, 10_000, 100);

            OrderResponse order = orderService.create(MEMBER_ID, orderOf(item(SCARCE, 5)));
            assertThat(stockOf(SCARCE)).isEqualTo(95);

            Outcome outcome = runConcurrently(10,
                    () -> orderService.cancel(MEMBER_ID, order.id()));

            assertThat(outcome.success()).as("취소는 한 번만 성공해야 한다").isEqualTo(1);
            assertThat(stockOf(SCARCE)).as("재고가 중복 복구되면 안 된다").isEqualTo(100);
        }
    }
}
