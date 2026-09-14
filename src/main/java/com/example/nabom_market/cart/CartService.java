package com.example.nabom_market.cart;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nabom_market.cart.dto.CartItemAddRequest;
import com.example.nabom_market.cart.dto.CartItemResponse;
import com.example.nabom_market.cart.dto.CartItemUpdateRequest;
import com.example.nabom_market.cart.dto.CartResponse;
import com.example.nabom_market.common.exception.BusinessException;
import com.example.nabom_market.common.exception.ErrorCode;
import com.example.nabom_market.product.ProductMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    public CartResponse getCart(Long memberId) {
        Cart cart = getOrCreateCart(memberId);

        List<CartItemResponse> items = cartItemMapper.findByMemberId(memberId).stream()
                .map(CartItemResponse::from)
                .toList();

        return CartResponse.of(cart.getId(), items);
    }

    @Transactional
    public CartResponse addItem(Long memberId, CartItemAddRequest request) {
        if (!productMapper.existsById(request.productId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다. id: " + request.productId());
        }

        Cart cart = getOrCreateCart(memberId);
        CartItem item = new CartItem(cart.getId(), request.productId(), request.quantity());
        cartItemMapper.upsert(item);

        return getCart(memberId);
    }

    @Transactional
    public CartResponse updateItem(Long memberId, Long itemId, CartItemUpdateRequest request) {
        if (cartItemMapper.findByIdAndMemberId(itemId, memberId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다. id: " + itemId);
        }

        cartItemMapper.updateQuantity(itemId, request.quantity());

        return getCart(memberId);
    }

    @Transactional
    public void removeItem(Long memberId, Long itemId) {
        if (!cartItemMapper.deleteByIdAndMemberId(itemId, memberId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다. id: " + itemId);
        }
    }

    private Cart getOrCreateCart(Long memberId) {
        Cart cart = cartMapper.findByMemberId(memberId);

        if (cart == null) {
            cartMapper.upsert(memberId);
            cart = cartMapper.findByMemberId(memberId);
        }

        return cart;
    }
}
