package com.example.nabom_market.cart;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public List<CartItem> getCartItems(@RequestParam String param) {
        return cartService.getItems(param);
    }

    @PostMapping
    public void addItemToCart(@RequestParam CartItemAddRequest param) {
        cartService.addItem(param);
    }

    @DeleteMapping("/items/{itemId}")
    public void removeItemFromCart(@RequestParam Long itemId) {
        cartService.removeItem(itemId);
    }

}
