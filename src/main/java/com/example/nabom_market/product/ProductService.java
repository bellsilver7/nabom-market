package com.example.nabom_market.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nabom_market.common.exception.BusinessException;
import com.example.nabom_market.common.exception.ErrorCode;
import com.example.nabom_market.product.dto.ProductCreateRequest;
import com.example.nabom_market.product.dto.ProductResponse;
import com.example.nabom_market.product.dto.ProductUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductMapper productMapper;

    public List<ProductResponse> findAll() {
        return productMapper.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return ProductResponse.from(getOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = request.toEntity();
        productMapper.insert(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = getOrThrow(id);
        product.update(request.name(), request.price(), request.stock());
        productMapper.update(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        if (productMapper.deleteById(id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다. id: " + id);
        }
    }

    private Product getOrThrow(Long id) {
        return productMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다. id: " + id));
    }

}
