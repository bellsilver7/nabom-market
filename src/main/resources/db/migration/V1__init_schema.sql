-- nabom-market 초기 스키마
--
-- 주의: 이 파일은 한 번 적용되면 수정할 수 없다.
-- Flyway가 체크섬을 flyway_schema_history에 저장하므로, 수정하면 다음 기동이 실패한다.
-- 개발 중 스키마를 바꿔야 하면 `docker compose down -v`로 볼륨까지 지우고 다시 올리거나,
-- 이미 공유된 뒤라면 V2, V3로 새 마이그레이션을 쌓는다.

-- ---------------------------------------------------------------------------
-- member
-- ---------------------------------------------------------------------------
CREATE TABLE member
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- product
--
-- price, stock은 INT다. 금액에 부동소수점(DOUBLE, FLOAT)을 쓰면 반올림 오차가 누적된다.
-- stock >= 0 CHECK 제약은 안전망이다. 재고 차감은 애플리케이션에서 조건부 UPDATE로
-- 처리하지만, 어떤 경로로든 음수 재고가 저장되는 일은 DB가 막아준다.
-- ---------------------------------------------------------------------------
CREATE TABLE product
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(255) NOT NULL,
    price      INT          NOT NULL,
    stock      INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- cart  (회원당 하나 — member_id에 UNIQUE)
-- ---------------------------------------------------------------------------
CREATE TABLE cart
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    member_id  BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_member (member_id),
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- cart_item
--
-- (cart_id, product_id) UNIQUE가 핵심이다.
-- 같은 상품을 두 번 담았을 때 행을 추가하지 않고 수량만 올리는 처리를
-- INSERT ... ON DUPLICATE KEY UPDATE 한 문장으로 끝낼 수 있다.
-- ---------------------------------------------------------------------------
CREATE TABLE cart_item
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    quantity   INT      NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item_cart_product (cart_id, product_id),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES cart (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- orders
--
-- 테이블명이 복수형인 이유: ORDER는 SQL 예약어라 백틱 없이는 쓸 수 없다.
-- status는 ENUM 대신 VARCHAR다. 상태를 추가할 때 DDL 변경이 필요 없고,
-- MyBatis가 Java enum과 이름으로 자동 매핑해 준다.
-- ---------------------------------------------------------------------------
CREATE TABLE orders
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    total_price INT         NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ordered_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_orders_member_ordered_at (member_id, ordered_at DESC),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_orders_total_price CHECK (total_price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- order_item
--
-- order_price는 "주문 시점의 상품 단가"다. (총액이 아니라 단가)
-- product를 조인해서 현재 가격을 읽으면, 상품 가격이 바뀌었을 때
-- 과거 주문의 금액까지 함께 바뀌어 버린다. 그래서 값을 복사해 보관한다.
-- 항목 금액 = quantity * order_price, 주문 총액 = 그 합계.
-- ---------------------------------------------------------------------------
CREATE TABLE order_item
(
    id          BIGINT NOT NULL AUTO_INCREMENT,
    order_id    BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT    NOT NULL,
    order_price INT    NOT NULL,

    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_price CHECK (order_price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
