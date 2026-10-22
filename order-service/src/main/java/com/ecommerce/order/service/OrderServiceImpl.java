package com.ecommerce.order.service;

import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.config.TraceIdWebFilter;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.OrderStatusHistory;
import com.ecommerce.order.domain.OrderStateMachine;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.InventoryReservationItem;
import com.ecommerce.order.dto.OrderHistoryItem;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.InventoryUnavailableException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.StockInsufficientException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
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
    private static final String REASON_STOCK_RESERVED = "Stock reserved";
    private static final String REASON_INSUFFICIENT_STOCK = "Insufficient stock";
    private static final String REASON_INVENTORY_UNAVAILABLE = "Inventory unavailable";
    private static final String REASON_CANCELLED_BY_CUSTOMER = "Cancelled by customer";
    private static final String NO_TRACE = "NO-TRACE";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderStateMachine orderStateMachine;
    private final OrderMapper orderMapper;
    private final TransactionalOperator transactionalOperator;
    private final InventoryClient inventoryClient;

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
                .newEntity(true)
                .build();

        // Tx 1 persists PENDING + items + history. The inventory call is not
        // transactional and is not compensable, so it must run outside of it;
        // CONFIRMED, FAILED and CANCELLED are therefore persisted afterwards in
        // their own transactions, which never roll back the committed rows.
        return orderRepository.save(order)
                .doOnNext(saved -> saved.setNewEntity(false))
                .flatMap(saved -> orderItemRepository.saveAll(toEntities(saved.getId(), request))
                        .collectList()
                        .flatMap(persisted -> saveHistory(saved.getId(), null, OrderStatus.PENDING,
                                REASON_ORDER_CREATED)
                                .thenReturn(persisted))
                        .as(transactionalOperator::transactional)
                        .flatMap(persisted -> inventoryClient
                                .reserve(saved.getId(), toReservationItems(request))
                                .then(Mono.defer(() -> confirmOrder(saved, persisted)))
                                .onErrorResume(error -> failOrder(saved, error).then(Mono.error(error)))))
                .doOnSuccess(response -> log.info("Order {} created for customer {} with status {}",
                        response.id(), response.customerId(), response.status()));
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
                    return orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId)
                            .collectList()
                            .flatMap(items -> {
                                if (from == OrderStatus.PENDING) {
                                    return persistCancellation(order, items);
                                }
                                return inventoryClient.release(orderId, toReservationItems(items))
                                        .then(Mono.defer(() -> persistCancellation(order, items)))
                                        .doOnSuccess(ignored -> log.info("Stock released for order {}", orderId));
                            });
                })
                .doOnSuccess(response -> log.info("Order {} cancelled from {}", response.id(), response.status()));
    }

    private Mono<OrderResponse> confirmOrder(Order order, List<OrderItem> items) {
        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order)
                .then(saveHistory(order.getId(), OrderStatus.PENDING, OrderStatus.CONFIRMED, REASON_STOCK_RESERVED))
                .as(transactionalOperator::transactional)
                .thenReturn(orderMapper.toResponse(order, items));
    }

    private Mono<Void> failOrder(Order order, Throwable error) {
        log.warn("Order {} failed: {}", order.getId(), error.getMessage());
        order.setStatus(OrderStatus.FAILED);
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order)
                .then(saveHistory(order.getId(), OrderStatus.PENDING, OrderStatus.FAILED, reasonFor(error)))
                .as(transactionalOperator::transactional)
                .then();
    }

    private String reasonFor(Throwable error) {
        return switch (error) {
            case StockInsufficientException ignored -> REASON_INSUFFICIENT_STOCK;
            case InventoryUnavailableException ignored -> REASON_INVENTORY_UNAVAILABLE;
            default -> "Order failed";
        };
    }

    private Mono<OrderResponse> persistCancellation(Order order, List<OrderItem> items) {
        return transitionToCancelled(order, items).as(transactionalOperator::transactional);
    }

    private Mono<OrderResponse> transitionToCancelled(Order order, List<OrderItem> items) {
        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order)
                .then(saveHistory(order.getId(), from, OrderStatus.CANCELLED, REASON_CANCELLED_BY_CUSTOMER))
                .thenReturn(orderMapper.toResponse(order, items));
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

    private List<InventoryReservationItem> toReservationItems(CreateOrderRequest request) {
        return request.items().stream()
                .map(item -> new InventoryReservationItem(item.productId(), item.quantity()))
                .toList();
    }

    private List<InventoryReservationItem> toReservationItems(List<OrderItem> items) {
        return items.stream()
                .map(item -> new InventoryReservationItem(item.getProductId(), item.getQuantity()))
                .toList();
    }

    private BigDecimal calculateTotal(CreateOrderRequest request) {
        return request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
