package com.example.nabom_market.product;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper {

    Optional<Product> findById(Long id);

    List<Product> findAll();

    void insert(Product product);

    int update(Product product);

    int deleteById(Long id);
}
