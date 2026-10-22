package com.ecommerce.order.service;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.OrderStatusHistory;
import com.ecommerce.order.domain.OrderStateMachineImpl;
import com.ecommerce.order.dto.CreateOrderItem;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.BusinessException;
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
import reactor.util.context.Context;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-4111-8b2d-111111111111");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, orderItemRepository, historyRepository,
                new OrderStateMachineImpl(), orderMapper(), transactionalOperator);
        lenient().when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OrderMapper orderMapper() {
        return new OrderMapperImpl();
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest("customer-1", List.of(
                new CreateOrderItem(PRODUCT_ID, 2, new BigDecimal("10.50")),
                new CreateOrderItem(UUID.fromString("22222222-2222-4222-8b2d-222222222222"), 1, new BigDecimal("5.00"))));
    }

    @Test
    void createPersistsOrderItemsAndHistoryInPendingState() {
        Order saved = Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("26.00"))
                .createdAt(java.time.Instant.now()).updatedAt(java.time.Instant.now()).build();
        OrderItem item = OrderItem.builder().id(1L).orderId(ORDER_ID).productId(PRODUCT_ID)
                .quantity(2).unitPrice(new BigDecimal("10.50")).build();
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(any(List.class))).thenReturn(Flux.just(item, item));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.create(request()))
                .expectNextMatches(response -> ORDER_ID.equals(response.id())
                        && response.status() == OrderStatus.PENDING
                        && new BigDecimal("26.00").compareTo(response.totalAmount()) == 0
                        && response.items().size() == 2)
                .verifyComplete();

        verify(historyRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    void createWritesHistoryFromNullToPending() {
        Order saved = Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("26.00")).build();
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(any(List.class))).thenReturn(Flux.just(
                OrderItem.builder().id(1L).orderId(ORDER_ID).productId(PRODUCT_ID)
                        .quantity(1).unitPrice(new BigDecimal("10.50")).build()));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.create(request()))
                .expectNextCount(1)
                .verifyComplete();

        verify(historyRepository).save(org.mockito.ArgumentMatchers.<OrderStatusHistory>argThat(h ->
                h.getFromStatus() == null && h.getToStatus() == OrderStatus.PENDING
                        && ORDER_ID.equals(h.getOrderId()) && h.getTraceId() != null));
    }

    @Test
    void cancelTransitionsConfirmedToCancelled() {
        Order confirmed = Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(OrderStatus.CONFIRMED).totalAmount(new BigDecimal("26.00")).build();
        OrderItem item = OrderItem.builder().id(1L).orderId(ORDER_ID).productId(PRODUCT_ID)
                .quantity(2).unitPrice(new BigDecimal("10.50")).build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(confirmed));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(Flux.just(item));

        StepVerifier.create(service.cancel(ORDER_ID))
                .expectNextMatches(response -> response.status() == OrderStatus.CANCELLED)
                .verifyComplete();
    }

    @Test
    void cancelTwiceFailsWithInvalidStateTransition() {
        Order cancelled = Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(OrderStatus.CANCELLED).totalAmount(new BigDecimal("26.00")).build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(cancelled));

        StepVerifier.create(service.cancel(ORDER_ID))
                .consumeErrorWith(error -> assertTrue(error instanceof BusinessException be
                        && "INVALID_STATE_TRANSITION".equals(be.getCode().name()),
                        "Unexpected error: " + error))
                .verify();
    }

    @Test
    void getByIdReturnsOrderWithItems() {
        Order order = Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("21.00")).build();
        OrderItem item = OrderItem.builder().id(1L).orderId(ORDER_ID).productId(PRODUCT_ID)
                .quantity(2).unitPrice(new BigDecimal("10.50")).build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(Flux.just(item));

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
                .reason("Order created").traceId("trace-123").changedAt(java.time.Instant.now()).build();
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
    void cancelHistoryRecordsFromStatusAndCancelledToStatus() {
        Order confirmed = Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(OrderStatus.CONFIRMED).totalAmount(new BigDecimal("26.00")).build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(confirmed));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(service.cancel(ORDER_ID))
                .expectNextCount(1)
                .verifyComplete();

        verify(historyRepository).save(org.mockito.ArgumentMatchers.<OrderStatusHistory>argThat(h ->
                h.getFromStatus() == OrderStatus.CONFIRMED && h.getToStatus() == OrderStatus.CANCELLED));
    }

    @Test
    void createComputesTotalFromItems() {
        CreateOrderRequest request = request();
        BigDecimal expected = new BigDecimal("26.00");

        Order saved = Order.builder().id(ORDER_ID).customerId("customer-1")
                .status(OrderStatus.PENDING).totalAmount(expected).build();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order toSave = inv.getArgument(0);
            assertEquals(0, expected.compareTo(toSave.getTotalAmount()));
            return Mono.just(saved);
        });
        lenient().when(orderItemRepository.saveAll(any(List.class))).thenReturn(Flux.empty());
        lenient().when(historyRepository.save(any(OrderStatusHistory.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.create(request))
                .expectNextCount(1)
                .verifyComplete();
    }
}
