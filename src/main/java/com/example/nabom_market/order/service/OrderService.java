package com.example.nabom_market.order.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nabom_market.common.exception.BusinessException;
import com.example.nabom_market.common.exception.ErrorCode;
import com.example.nabom_market.order.domain.Order;
import com.example.nabom_market.order.domain.OrderItem;
import com.example.nabom_market.order.domain.OrderItemView;
import com.example.nabom_market.order.dto.OrderCreateRequest;
import com.example.nabom_market.order.dto.OrderItemCreateRequest;
import com.example.nabom_market.order.dto.OrderItemResponse;
import com.example.nabom_market.order.dto.OrderResponse;
import com.example.nabom_market.order.mapper.OrderItemMapper;
import com.example.nabom_market.order.mapper.OrderMapper;
import com.example.nabom_market.product.Product;
import com.example.nabom_market.product.ProductMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;

    @Transactional
    public OrderResponse create(Long memberId, OrderCreateRequest request) {

        List<OrderItem> items = new ArrayList<>();
        int totalPrice = 0;

        // 1. 상품 조회 및 합계
        for (OrderItemCreateRequest v : request.items()) {
            Product product = productMapper.findById(v.productId()).orElseThrow(
                    () -> new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다. id: " + v.productId()));
            OrderItem item = new OrderItem(product.getId(), v.quantity(), product.getPrice());

            items.add(item);
            totalPrice += item.totalPrice();
        }

        // 2. 재고 차감
        for (OrderItem item : items) {
            boolean deducted = productMapper.deductStock(item.getProductId(), item.getQuantity());

            if (!deducted) {
                throw new BusinessException(ErrorCode.OUT_OF_STOCK, "재고가 부족합니다. productId: " + item.getProductId());
            }
        }

        // 3. 주문 저장
        Order order = new Order(memberId, totalPrice);
        orderMapper.insert(order);

        for (OrderItem item : items) {
            item.assignOrder(order.getId());
        }
        orderItemMapper.bulkInsert(items);

        return findById(memberId, order.getId());
    }

    @Transactional
    public OrderResponse cancel(Long memberId, Long orderId) {
        getOrThrow(memberId, orderId);

        if (!orderMapper.cancelIfPending(orderId, memberId)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "취소할 수 없는 주문입니다.");
        }

        for (OrderItemView view : orderItemMapper.findAllByOrderId(orderId)) {
            productMapper.restoreStock(view.getProductId(), view.getQuantity());
        }

        return findById(memberId, orderId);
    }

    public List<OrderResponse> findAll(Long memberId) {
        List<Order> orders = orderMapper.findAllByMemberId(memberId);

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = new ArrayList<>();
        for (Order order : orders) {
            orderIds.add(order.getId());
        }

        List<OrderItemView> allItems = orderItemMapper.findAllByOrderIds(orderIds);

        Map<Long, List<OrderItemResponse>> itemsByOrderId = new HashMap<>();
        for (OrderItemView view : allItems) {
            itemsByOrderId.computeIfAbsent(view.getOrderId(), key -> new ArrayList<>())
                    .add(OrderItemResponse.from(view));
        }

        List<OrderResponse> responses = new ArrayList<>();
        for (Order order : orders) {
            List<OrderItemResponse> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
            responses.add(OrderResponse.of(order, items));
        }

        return responses;
    }

    public OrderResponse findById(Long memberId, Long orderId) {
        Order order = getOrThrow(memberId, orderId);

        List<OrderItemResponse> items = new ArrayList<>();
        for (OrderItemView view : orderItemMapper.findAllByOrderId(orderId)) {
            items.add(OrderItemResponse.from(view));
        }
        return OrderResponse.of(order, items);
    }

    private Order getOrThrow(Long memberId, Long orderId) {
        return orderMapper.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "주문을 찾을 수 없습니다. id: " + orderId));
    }

}
