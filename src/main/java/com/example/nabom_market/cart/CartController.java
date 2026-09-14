package com.example.nabom_market.cart;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.nabom_market.cart.dto.CartItemAddRequest;
import com.example.nabom_market.cart.dto.CartItemUpdateRequest;
import com.example.nabom_market.cart.dto.CartResponse;
import com.example.nabom_market.common.security.LoginMember;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@LoginMember Long memberId) {
        return cartService.getCart(memberId);
    }

    @PostMapping("/items")
    public CartResponse addItemToCart(@LoginMember Long memberId,
            @Valid @RequestBody CartItemAddRequest request) {
        return cartService.addItem(memberId, request);
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse updateItem(@LoginMember Long memberId, @PathVariable Long itemId,
            @Valid @RequestBody CartItemUpdateRequest request) {
        return cartService.updateItem(memberId, itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItemFromCart(@LoginMember Long memberId, @PathVariable Long itemId) {
        cartService.removeItem(memberId, itemId);
    }

}
