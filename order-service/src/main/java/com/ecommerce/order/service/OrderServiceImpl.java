package com.ecommerce.order.service;

import com.ecommerce.order.config.TraceIdWebFilter;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.OrderStatusHistory;
import com.ecommerce.order.domain.OrderStateMachine;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderHistoryItem;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String REASON_ORDER_CREATED = "Order created";
    private static final String REASON_ORDER_CANCELLED = "Order cancelled by request";
    private static final String NO_TRACE = "NO-TRACE";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderStateMachine orderStateMachine;
    private final OrderMapper orderMapper;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<OrderResponse> create(CreateOrderRequest request) {
        BigDecimal totalAmount = calculateTotal(request);
        Instant now = Instant.now();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customerId(request.customerId())
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return orderRepository.save(order)
                .flatMap(saved -> {
                    List<OrderItem> items = toEntities(saved.getId(), request);
                    return orderItemRepository.saveAll(items)
                            .collectList()
                            .flatMap(persistedItems -> saveHistory(saved.getId(), null, OrderStatus.PENDING,
                                    REASON_ORDER_CREATED)
                                    .thenReturn(orderMapper.toResponse(saved, persistedItems)));
                })
                .as(transactionalOperator::transactional)
                .doOnSuccess(response -> log.info("Order {} created for customer {} with status PENDING",
                        response.id(), response.customerId()));
    }

    @Override
    public Mono<OrderResponse> getById(UUID orderId) {
        return findOrderWithItems(orderId);
    }

    @Override
    public Mono<List<OrderHistoryItem>> getHistory(UUID orderId) {
        return orderRepository.existsById(orderId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .thenMany(historyRepository.findAllByOrderIdOrderByChangedAtAsc(orderId))
                .map(orderMapper::toHistoryItem)
                .collectList();
    }

    @Override
    public Mono<OrderResponse> cancel(UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> {
                    OrderStatus from = order.getStatus();
                    orderStateMachine.validate(from, OrderStatus.CANCELLED);
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setUpdatedAt(Instant.now());
                    return orderRepository.save(order)
                            .then(saveHistory(orderId, from, OrderStatus.CANCELLED, REASON_ORDER_CANCELLED))
                            .then(orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId).collectList())
                            .map(items -> orderMapper.toResponse(order, items));
                })
                .as(transactionalOperator::transactional)
                .doOnSuccess(response -> log.info("Order {} cancelled from {}", response.id(), response.status()));
    }

    private Mono<OrderResponse> findOrderWithItems(UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId)
                        .collectList()
                        .map(items -> orderMapper.toResponse(order, items)));
    }

    private Mono<Void> saveHistory(UUID orderId, OrderStatus from, OrderStatus to, String reason) {
        return currentTraceId()
                .map(traceId -> OrderStatusHistory.builder()
                        .orderId(orderId)
                        .fromStatus(from)
                        .toStatus(to)
                        .reason(reason)
                        .traceId(traceId)
                        .changedAt(Instant.now())
                        .build())
                .flatMap(historyRepository::save)
                .then();
    }

    private Mono<String> currentTraceId() {
        return Mono.deferContextual(ctx ->
                Mono.just(ctx.getOrDefault(TraceIdWebFilter.CONTEXT_KEY, NO_TRACE).toString()));
    }

    private List<OrderItem> toEntities(UUID orderId, CreateOrderRequest request) {
        return request.items().stream()
                .map(item -> OrderItem.builder()
                        .orderId(orderId)
                        .productId(item.productId())
                        .quantity(item.quantity())
                        .unitPrice(item.unitPrice())
                        .build())
                .toList();
    }

    private BigDecimal calculateTotal(CreateOrderRequest request) {
        return request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
