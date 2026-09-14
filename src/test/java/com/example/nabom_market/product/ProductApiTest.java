package com.example.nabom_market.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/**
 * 상품 API 명세 검증.
 *
 * <p>
 * 상품 조회는 공개 API다. 토큰 없이 호출한다.
 *
 * <p>
 * 샘플 데이터 10건 (가격 오름차순):
 *
 * <pre>
 *  id  가격      재고   이름
 *   9  12,000   200   무형광 순면 행주 5매
 *  10  15,000    75   스테인리스 계량스푼 4종
 *   2  18,500   120   유기농 원두 에티오피아 예가체프 200g
 *   4  24,000    60   수제 도자기 머그 280ml
 *   8  27,000     0   왕겨 베개 커버 2p          ← 품절
 *   6  29,000    35   핸드드립 세라믹 드리퍼
 *   3  32,000    40   리넨 앞치마 차콜
 *   5  41,000    18   대나무 도마 라지
 *   1  68,000    25   무쇠 주물 프라이팬 24cm
 *   7  89,000     3   한정판 캠핑 케틀 1.2L
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("상품 API")
class ProductApiTest {

    private static final String PRODUCTS = "/api/v1/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 주문 테스트가 재고를 깎아 놓았을 수 있다. 샘플 값으로 되돌린다.
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

        // 앞선 테스트가 남긴 상품 제거
        jdbcTemplate.update("DELETE FROM product WHERE id > 10");
    }

    // ------------------------------------------------------------------ helpers

    private String productBody(String name, int price, int stock) {
        return """
                {"name": "%s", "price": %d, "stock": %d}
                """.formatted(name, price, stock);
    }

    /** 검색용 상품을 직접 넣는다. */
    private void insertProduct(String name, int price, int stock) {
        jdbcTemplate.update(
                "INSERT INTO product (name, price, stock) VALUES (?, ?, ?)", name, price, stock);
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("목록 조회 GET /api/v1/products")
    class FindAll {

        @Test
        @DisplayName("조건이 없으면 전체를 페이지로 감싸 내려준다")
        void returnsPagedContent() throws Exception {
            mockMvc.perform(get(PRODUCTS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(10))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(10))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("항목은 id, 이름, 가격, 재고를 담는다")
        void itemHasFields() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("keyword", "프라이팬"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("무쇠 주물 프라이팬 24cm"))
                    .andExpect(jsonPath("$.content[0].price").value(68000))
                    .andExpect(jsonPath("$.content[0].stock").value(25));
        }

        @Test
        @DisplayName("토큰 없이 접근할 수 있다")
        void isPublic() throws Exception {
            mockMvc.perform(get(PRODUCTS))
                    .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("검색")
    class Search {

        @Test
        @DisplayName("keyword 는 상품명 부분 일치로 걸러낸다")
        void filtersByKeyword() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("keyword", "도자기"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(4))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("keyword 는 여러 건을 찾을 수 있다")
        void keywordMatchesMany() throws Exception {
            insertProduct("원두 보관 캐니스터", 22000, 10);

            mockMvc.perform(get(PRODUCTS).param("keyword", "원두"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("일치하는 상품이 없으면 빈 배열과 총 0건")
        void emptyResult() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("keyword", "존재하지않는상품명"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("빈 keyword 는 조건으로 취급하지 않는다")
        void blankKeywordIsIgnored() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("keyword", "   "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(10));
        }

        @Test
        @DisplayName("minPrice 이상만 남긴다")
        void filtersByMinPrice() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("minPrice", "41000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3)); // 41,000 / 68,000 / 89,000
        }

        @Test
        @DisplayName("maxPrice 이하만 남긴다")
        void filtersByMaxPrice() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("maxPrice", "18500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3)); // 12,000 / 15,000 / 18,500
        }

        @Test
        @DisplayName("가격 범위는 양끝을 포함한다")
        void priceRangeIsInclusive() throws Exception {
            mockMvc.perform(get(PRODUCTS)
                    .param("minPrice", "24000")
                    .param("maxPrice", "32000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4)); // 24,000 / 27,000 / 29,000 / 32,000
        }

        @Test
        @DisplayName("inStock=true 면 품절 상품을 제외한다")
        void excludesSoldOut() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("inStock", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(9)); // id 8 (재고 0) 제외
        }

        @Test
        @DisplayName("inStock 을 주지 않으면 품절 상품도 보인다")
        void includesSoldOutByDefault() throws Exception {
            mockMvc.perform(get(PRODUCTS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(10));
        }

        @Test
        @DisplayName("여러 조건은 AND 로 결합된다")
        void combinesConditions() throws Exception {
            // keyword 만: 3건 (무쇠 68,000 / 대나무 41,000 / 무형광 12,000)
            // minPrice 만: 2건 (68,000 / 89,000)
            // 둘 다:      1건
            mockMvc.perform(get(PRODUCTS)
                    .param("keyword", "무")
                    .param("minPrice", "50000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(1)); // 무쇠 주물 프라이팬 68,000
        }

        @Test
        @DisplayName("LIKE 와일드카드를 입력해도 문자 그대로 취급한다")
        void wildcardIsEscaped() throws Exception {
            insertProduct("100% 순면 손수건", 9000, 5);

            mockMvc.perform(get(PRODUCTS).param("keyword", "%"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("페이징")
    class Paging {

        @Test
        @DisplayName("size 만큼만 내려주고 다음 페이지가 있음을 알린다")
        void firstPage() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("size", "4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(4))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(4))
                    .andExpect(jsonPath("$.totalElements").value(10))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true));
        }

        @Test
        @DisplayName("마지막 페이지는 남은 만큼만 내려주고 hasNext 는 false")
        void lastPage() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("size", "4").param("page", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("범위를 넘은 페이지는 빈 배열")
        void pageBeyondRange() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("size", "4").param("page", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(10));
        }

        @Test
        @DisplayName("페이지가 겹치지 않는다")
        void pagesDoNotOverlap() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("size", "5").param("page", "0").param("sort", "price_asc"))
                    .andExpect(jsonPath("$.content[0].id").value(9))   // 12,000
                    .andExpect(jsonPath("$.content[4].id").value(8));  // 27,000

            mockMvc.perform(get(PRODUCTS).param("size", "5").param("page", "1").param("sort", "price_asc"))
                    .andExpect(jsonPath("$.content[0].id").value(6))   // 29,000
                    .andExpect(jsonPath("$.content[4].id").value(7));  // 89,000
        }

        @Test
        @DisplayName("size 는 상한을 넘지 못한다")
        void sizeIsCapped() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("size", "9999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        @DisplayName("음수 page 와 0 이하 size 는 기본값으로 되돌린다")
        void invalidPagingFallsBackToDefault() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("page", "-1").param("size", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("정렬")
    class Sorting {

        @Test
        @DisplayName("기본은 최신순 — 같은 시각이면 id 내림차순")
        void defaultIsLatest() throws Exception {
            mockMvc.perform(get(PRODUCTS))
                    .andExpect(jsonPath("$.content[0].id").value(10))
                    .andExpect(jsonPath("$.content[9].id").value(1));
        }

        @Test
        @DisplayName("price_asc 는 가격 오름차순")
        void priceAscending() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("sort", "price_asc"))
                    .andExpect(jsonPath("$.content[0].price").value(12000))
                    .andExpect(jsonPath("$.content[9].price").value(89000));
        }

        @Test
        @DisplayName("price_desc 는 가격 내림차순")
        void priceDescending() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("sort", "price_desc"))
                    .andExpect(jsonPath("$.content[0].price").value(89000))
                    .andExpect(jsonPath("$.content[9].price").value(12000));
        }

        @Test
        @DisplayName("알 수 없는 sort 값은 기본 정렬로 처리한다")
        void unknownSortFallsBack() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("sort", "price ASC; DROP TABLE product"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("단건 조회 GET /api/v1/products/{id}")
    class FindById {

        @Test
        @DisplayName("상품 정보를 내려준다")
        void returnsProduct() throws Exception {
            mockMvc.perform(get(PRODUCTS + "/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.name").value("한정판 캠핑 케틀 1.2L"))
                    .andExpect(jsonPath("$.price").value(89000))
                    .andExpect(jsonPath("$.stock").value(3));
        }

        @Test
        @DisplayName("없는 상품은 404")
        void notFound() throws Exception {
            mockMvc.perform(get(PRODUCTS + "/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("등록 POST /api/v1/products")
    class Create {

        @Test
        @DisplayName("생성하고 Location 헤더를 준다")
        void creates() throws Exception {
            mockMvc.perform(post(PRODUCTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(productBody("참나무 트레이", 38000, 12)))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("참나무 트레이"))
                    .andExpect(jsonPath("$.price").value(38000));
        }

        @Test
        @DisplayName("등록한 상품은 검색된다")
        void createdIsSearchable() throws Exception {
            mockMvc.perform(post(PRODUCTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(productBody("참나무 트레이", 38000, 12)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get(PRODUCTS).param("keyword", "참나무"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("상품명이 비면 400")
        void blankNameIsBadRequest() throws Exception {
            mockMvc.perform(post(PRODUCTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(productBody("", 38000, 12)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("가격이 음수면 400")
        void negativePriceIsBadRequest() throws Exception {
            mockMvc.perform(post(PRODUCTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(productBody("참나무 트레이", -1, 12)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("가격이 없으면 400")
        void missingPriceIsBadRequest() throws Exception {
            mockMvc.perform(post(PRODUCTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "참나무 트레이", "stock": 12}
                            """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("수정 PUT /api/v1/products/{id}")
    class Update {

        @Test
        @DisplayName("수정한 값이 반영된다")
        void updates() throws Exception {
            mockMvc.perform(put(PRODUCTS + "/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(productBody("리넨 앞치마 아이보리", 35000, 44)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("리넨 앞치마 아이보리"))
                    .andExpect(jsonPath("$.price").value(35000));

            mockMvc.perform(get(PRODUCTS + "/3"))
                    .andExpect(jsonPath("$.stock").value(44));
        }

        @Test
        @DisplayName("없는 상품은 404")
        void notFound() throws Exception {
            mockMvc.perform(put(PRODUCTS + "/999999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(productBody("없는 상품", 1000, 1)))
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("삭제 DELETE /api/v1/products/{id}")
    class Delete {

        @Test
        @DisplayName("삭제하면 204, 이후 조회는 404")
        void deletes() throws Exception {
            insertProduct("임시 상품", 1000, 1);
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM product ORDER BY id DESC LIMIT 1", Long.class);

            mockMvc.perform(delete(PRODUCTS + "/" + id))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(PRODUCTS + "/" + id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("없는 상품은 404")
        void notFound() throws Exception {
            mockMvc.perform(delete(PRODUCTS + "/999999"))
                    .andExpect(status().isNotFound());
        }
    }
}
