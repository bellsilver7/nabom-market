package com.example.nabom_market.product.dto;

import java.util.Set;

public record ProductSearchCondition(
        String keyword,
        Integer minPrice,
        Integer maxPrice,
        Boolean inStock,
        String sort,
        Integer page,
        Integer size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORTS = Set.of("latest", "price_asc", "price_desc");

    public ProductSearchCondition {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        inStock = (inStock != null) && inStock;
        sort = (sort != null && SORTS.contains(sort)) ? sort : "latest";
        page = (page == null || page < 0) ? 0 : page;
        size = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public int offset() {
        return page * size;
    }

    public String keywordPattern() {
        if (keyword == null) {
            return null;
        }

        String escaped = keyword
                .replace("!", "!!") // 이스케이프 문자 자신을 먼저
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
    }
}
