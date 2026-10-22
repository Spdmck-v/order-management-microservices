package com.ecommerce.order.service;

import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.OrderStatusHistory;
import com.ecommerce.order.domain.OrderStateMachineImpl;
import com.ecommerce.order.dto.CreateOrderItem;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.BusinessException;
import com.ecommerce.order.exception.InventoryUnavailableException;
import com.ecommerce.order.exception.ProductNotFoundException;
import com.ecommerce.order.exception.StockInsufficientException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.mapper.OrderMapperImpl;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.context.Context;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-4111-8b2d-111111111111");
    private static final UUID SECOND_PRODUCT_ID = UUID.fromString("22222222-2222-4222-8b2d-222222222222");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private InventoryClient inventoryClient;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, orderItemRepository, historyRepository,
                new OrderStateMachineImpl(), orderMapper(), transactionalOperator, inventoryClient);
        lenient().when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OrderMapper orderMapper() {
        return new OrderMapperImpl();
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest("customer-1", List.of(
                new CreateOrderItem(PRODUCT_ID, 2, new BigDecimal("10.50")),
                new CreateOrderItem(SECOND_PRODUCT_ID, 1, new BigDecimal("5.00"))));
    }

    private Order orderIn(OrderStatus status) {
        return Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(status).totalAmount(new BigDecimal("26.00"))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private void stubPersist() {
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(historyRepository.save(any(OrderStatusHistory.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private List<OrderItem> itemsFor(Order order) {
        OrderItem item = OrderItem.builder().id(1L).orderId(ORDER_ID).productId(PRODUCT_ID)
                .quantity(2).unitPrice(new BigDecimal("10.50")).build();
        OrderItem second = OrderItem.builder().id(2L).orderId(ORDER_ID).productId(SECOND_PRODUCT_ID)
                .quantity(1).unitPrice(new BigDecimal("5.00")).build();
        return List.of(item, second);
    }

    @Test
    void createReservesStockAndTransitionsToConfirmed() {
        Order saved = orderIn(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(itemsFor(saved).get(0), itemsFor(saved).get(1)));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(inventoryClient.reserve(any(UUID.class), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(service.create(request()))
                .expectNextMatches(response -> ORDER_ID.equals(response.id())
                        && response.status() == OrderStatus.CONFIRMED
                        && new BigDecimal("26.00").compareTo(response.totalAmount()) == 0
                        && response.items().size() == 2)
                .verifyComplete();

        verify(inventoryClient).reserve(any(UUID.class), argThat(reservedItems ->
                reservedItems.size() == 2
                        && PRODUCT_ID.equals(reservedItems.get(0).productId())
                        && reservedItems.get(0).quantity() == 2));
        verify(historyRepository, times(2)).save(any(OrderStatusHistory.class));
        verify(historyRepository).save(argThat(h ->
                h.getFromStatus() == OrderStatus.PENDING && h.getToStatus() == OrderStatus.CONFIRMED
                        && "Stock reserved".equals(h.getReason())));
        assertEquals(OrderStatus.CONFIRMED, saved.getStatus());
    }

    @Test
    void createWritesHistoryFromNullToPending() {
        Order saved = orderIn(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(itemsFor(saved).get(0)));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(inventoryClient.reserve(any(UUID.class), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(service.create(request()))
                .expectNextCount(1)
                .verifyComplete();

        verify(historyRepository).save(argThat(h ->
                h.getFromStatus() == null && h.getToStatus() == OrderStatus.PENDING
                        && ORDER_ID.equals(h.getOrderId()) && h.getTraceId() != null));
    }

    @Test
    void createFailsWith409AndOrderFailedWhenStockInsufficient() {
        Order saved = orderIn(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(itemsFor(saved).get(0)));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(inventoryClient.reserve(any(UUID.class), anyList())).thenReturn(Mono.error(new StockInsufficientException()));

        StepVerifier.create(service.create(request()))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "STOCK_INSUFFICIENT".equals(be.getCode().name()),
                        "Unexpected error: " + error))
                .verify();

        assertEquals(OrderStatus.FAILED, saved.getStatus());
        verify(historyRepository).save(argThat(h ->
                h.getFromStatus() == OrderStatus.PENDING && h.getToStatus() == OrderStatus.FAILED
                        && "Insufficient stock".equals(h.getReason())));
    }

    @Test
    void createFailsWith503AndOrderFailedWhenInventoryUnavailable() {
        Order saved = orderIn(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(itemsFor(saved).get(0)));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(inventoryClient.reserve(any(UUID.class), anyList())).thenReturn(Mono.error(new InventoryUnavailableException()));

        StepVerifier.create(service.create(request()), StepVerifierOptions.create().withInitialContext(
                        Context.of("traceId", "trace-abc")))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "INVENTORY_UNAVAILABLE".equals(be.getCode().name())
                        && be.getStatus().value() == 503,
                        "Unexpected error: " + error))
                .verify();

        assertEquals(OrderStatus.FAILED, saved.getStatus());
        verify(historyRepository).save(argThat(h ->
                h.getFromStatus() == OrderStatus.PENDING && h.getToStatus() == OrderStatus.FAILED
                        && "Inventory unavailable".equals(h.getReason())
                        && "trace-abc".equals(h.getTraceId())));
    }

    @Test
    void createFailsWith404AndOrderFailedWhenProductNotFound() {
        Order saved = orderIn(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(itemsFor(saved).get(0)));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(inventoryClient.reserve(any(UUID.class), anyList())).thenReturn(Mono.error(new ProductNotFoundException()));

        StepVerifier.create(service.create(request()))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "PRODUCT_NOT_FOUND".equals(be.getCode().name()),
                        "Unexpected error: " + error))
                .verify();

        assertEquals(OrderStatus.FAILED, saved.getStatus());
    }

    @Test
    void cancelTransitionsConfirmedToCancelledAndReleasesStock() {
        Order confirmed = orderIn(OrderStatus.CONFIRMED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(confirmed));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(Flux.fromIterable(itemsFor(confirmed)));
        when(inventoryClient.release(eq(ORDER_ID), anyList())).thenReturn(Mono.empty());
        stubPersist();

        StepVerifier.create(service.cancel(ORDER_ID))
                .expectNextMatches(response -> response.status() == OrderStatus.CANCELLED)
                .verifyComplete();

        verify(inventoryClient).release(eq(ORDER_ID), anyList());
        verify(historyRepository).save(argThat(h ->
                h.getFromStatus() == OrderStatus.CONFIRMED && h.getToStatus() == OrderStatus.CANCELLED
                        && "Cancelled by customer".equals(h.getReason())));
        assertEquals(OrderStatus.CANCELLED, confirmed.getStatus());
    }

    @Test
    void cancelTwiceFailsWithInvalidStateTransitionAndSkipsSecondRelease() {
        Order cancelled = orderIn(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(service.cancel(ORDER_ID))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "INVALID_STATE_TRANSITION".equals(be.getCode().name()),
                        "Unexpected error: " + error))
                .verify();

        verifyNoInteractions(inventoryClient);
    }

    @Test
    void cancelFromFailedFailsWithInvalidStateTransition() {
        Order failed = orderIn(OrderStatus.FAILED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(failed));

        StepVerifier.create(service.cancel(ORDER_ID))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "INVALID_STATE_TRANSITION".equals(be.getCode().name()),
                        "Unexpected error: " + error))
                .verify();

        verifyNoInteractions(inventoryClient);
    }

    @Test
    void cancelFromPendingSkipsRelease() {
        Order pending = orderIn(OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(pending));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(Flux.fromIterable(itemsFor(pending)));
        stubPersist();

        StepVerifier.create(service.cancel(ORDER_ID))
                .expectNextMatches(response -> response.status() == OrderStatus.CANCELLED)
                .verifyComplete();

        verify(inventoryClient, never()).release(any(UUID.class), anyList());
    }

    @Test
    void cancelKeepsConfirmedWhenReleaseFails() {
        Order confirmed = orderIn(OrderStatus.CONFIRMED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(confirmed));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(Flux.fromIterable(itemsFor(confirmed)));
        when(inventoryClient.release(eq(ORDER_ID), anyList()))
                .thenReturn(Mono.error(new InventoryUnavailableException()));
        stubPersist();

        StepVerifier.create(service.cancel(ORDER_ID))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "INVENTORY_UNAVAILABLE".equals(be.getCode().name())
                        && be.getStatus().value() == 503,
                        "Unexpected error: " + error))
                .verify();

        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
        verify(historyRepository, never()).save(any(OrderStatusHistory.class));
    }

    @Test
    void getByIdReturnsOrderWithItems() {
        Order order = orderIn(OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(Flux.just(itemsFor(order).get(0)));

        StepVerifier.create(service.getById(ORDER_ID))
                .expectNextMatches(response -> response.items().size() == 1
                        && PRODUCT_ID.equals(response.items().get(0).productId())
                        && response.items().get(0).quantity() == 2)
                .verifyComplete();
    }

    @Test
    void historyIsExposedWithTraceIdAndNullFromStatus() {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .id(1L).orderId(ORDER_ID).fromStatus(null).toStatus(OrderStatus.PENDING)
                .reason("Order created").traceId("trace-123").changedAt(Instant.now()).build();
        when(orderRepository.existsById(ORDER_ID)).thenReturn(Mono.just(true));
        when(historyRepository.findAllByOrderIdOrderByChangedAtAsc(ORDER_ID)).thenReturn(Flux.just(history));

        StepVerifier.create(service.getHistory(ORDER_ID).contextWrite(Context.of("traceId", "trace-123")))
                .expectNextMatches(items -> items.size() == 1
                        && items.get(0).fromStatus() == null
                        && "trace-123".equals(items.get(0).traceId()))
                .verifyComplete();
    }

    @Test
    void getByIdFailsWithOrderNotFoundForUnknownId() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getById(ORDER_ID))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "ORDER_NOT_FOUND".equals(be.getCode().name()),
                        "Unexpected error: " + error))
                .verify();
    }

    @Test
    void createComputesTotalFromItems() {
        CreateOrderRequest request = request();
        BigDecimal expected = new BigDecimal("26.00");

        Order saved = orderIn(OrderStatus.PENDING);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order toSave = inv.getArgument(0);
            assertEquals(0, expected.compareTo(toSave.getTotalAmount()));
            return Mono.just(saved);
        });
        lenient().when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.empty());
        lenient().when(historyRepository.save(any(OrderStatusHistory.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(inventoryClient.reserve(any(UUID.class), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(service.create(request))
                .expectNextCount(1)
                .verifyComplete();
    }
}
