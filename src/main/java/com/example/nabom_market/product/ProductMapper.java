package com.example.nabom_market.product;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.example.nabom_market.product.dto.ProductSearchCondition;

@Mapper
public interface ProductMapper {

    Optional<Product> findById(Long id);

    List<Product> findAll();

    List<Product> search(ProductSearchCondition condition);

    long countBySearch(ProductSearchCondition condition);

    void insert(Product product);

    int update(Product product);

    int deleteById(Long id);

    boolean existsById(Long id);

    boolean deductStock(Long productId, int quantity);

    void restoreStock(Long productId, int quantity);
}
