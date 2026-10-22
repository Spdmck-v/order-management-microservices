package com.ecommerce.order.service;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderHistoryItem;
import com.ecommerce.order.dto.OrderResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    Mono<OrderResponse> create(CreateOrderRequest request);

    Mono<OrderResponse> getById(UUID orderId);

    Mono<List<OrderHistoryItem>> getHistory(UUID orderId);

    Mono<OrderResponse> cancel(UUID orderId);
}
