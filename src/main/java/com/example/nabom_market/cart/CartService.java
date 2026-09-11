package com.example.nabom_market.cart;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;

    public List<CartItem> getItems(Long cartId) {
        return cartItemMapper.findByCartId(cartId);
    }

    @Transactional
    public void addItem(Long memberId, Long cartId, String productId, int quantity) {
        Cart cart = new Cart(memberId);
        CartItem cartItem = new CartItem(cartId, productId, quantity);
        cartItemMapper.insert(cartItem);
    }

    @Transactional
    public void removeItem(Long itemId) {
        if (cartItemMapper.deleteById(itemId) == 0) {
            throw new IllegalArgumentException("Cart item not found with id: " + itemId);
        }
    }
}
