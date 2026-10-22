package com.ecommerce.inventory.service;

import com.ecommerce.inventory.domain.Product;
import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;
import com.ecommerce.inventory.dto.OperationItemResponse;
import com.ecommerce.inventory.dto.ReleaseItemRequest;
import com.ecommerce.inventory.dto.ReleaseRequest;
import com.ecommerce.inventory.dto.ReserveItemRequest;
import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.exception.BusinessException;
import com.ecommerce.inventory.exception.StockInsufficientException;
import com.ecommerce.inventory.repository.AtomicStockOperations;
import com.ecommerce.inventory.repository.ProductRepository;
import com.ecommerce.inventory.repository.ReservationRepository;
import com.ecommerce.inventory.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-4111-8b2d-111111111111");

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private AtomicStockOperations atomicStockOperations;
    @Mock
    private TransactionalOperator transactionalOperator;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(productRepository, stockRepository, reservationRepository,
                atomicStockOperations, null, transactionalOperator);
        lenient().when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reserveReturnsInsufficientStockWhenAtomicUpdateAffectsNoRows() {
        Product product = Product.builder().id(PRODUCT_ID).sku("SKU-001").name("Keyboard").active(true).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Mono.just(product));
        when(reservationRepository.find(ORDER_ID, PRODUCT_ID)).thenReturn(Mono.empty());
        when(atomicStockOperations.reserveStock(PRODUCT_ID, 5)).thenReturn(Mono.just(0L));

        var request = new ReserveRequest(ORDER_ID, List.of(new ReserveItemRequest(PRODUCT_ID, 5)));

        StepVerifier.create(service.reserve(request))
                .consumeErrorWith(error -> {
                    if (error instanceof BusinessException be) {
                        org.junit.jupiter.api.Assertions.assertEquals("STOCK_INSUFFICIENT", be.getCode().name());
                    } else {
                        throw new IllegalStateException("Unexpected error type: " + error);
                    }
                })
                .verify();
    }

    @Test
    void reserveIsIdempotentWhenReservationAlreadyReserved() {
        Product product = Product.builder().id(PRODUCT_ID).sku("SKU-001").name("Keyboard").active(true).build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Mono.just(product));
        when(reservationRepository.find(ORDER_ID, PRODUCT_ID))
                .thenReturn(Mono.just(new StockReservation(ORDER_ID, PRODUCT_ID, 5, ReservationStatus.RESERVED)));

        var request = new ReserveRequest(ORDER_ID, List.of(new ReserveItemRequest(PRODUCT_ID, 5)));

        StepVerifier.create(service.reserve(request))
                .expectNextMatches(response -> ORDER_ID.equals(response.orderId())
                        && response.items().size() == 1
                        && "ALREADY_RESERVED".equals(response.items().get(0).status()))
                .verifyComplete();
    }

    @Test
    void releaseIsNoOpWhenNoReservedStockExists() {
        when(reservationRepository.find(ORDER_ID, PRODUCT_ID)).thenReturn(Mono.empty());

        var request = new ReleaseRequest(ORDER_ID, List.of(new ReleaseItemRequest(PRODUCT_ID, 5)));

        StepVerifier.create(service.release(request))
                .expectNextMatches(response -> response.items().size() == 1
                        && "NOT_RESERVED".equals(response.items().get(0).status()))
                .verifyComplete();
    }

    @Test
    void releaseRestoresStockAndMarksReservationReleased() {
        when(reservationRepository.find(ORDER_ID, PRODUCT_ID))
                .thenReturn(Mono.just(new StockReservation(ORDER_ID, PRODUCT_ID, 5, ReservationStatus.RESERVED)));
        when(atomicStockOperations.releaseStock(PRODUCT_ID, 5)).thenReturn(Mono.just(1L));
        when(reservationRepository.markReleased(ORDER_ID, PRODUCT_ID)).thenReturn(Mono.just(1L));

        var request = new ReleaseRequest(ORDER_ID, List.of(new ReleaseItemRequest(PRODUCT_ID, 5)));

        StepVerifier.create(service.release(request))
                .expectNextMatches(response -> response.items().size() == 1
                        && "RELEASED".equals(response.items().get(0).status())
                        && Integer.valueOf(5).equals(response.items().get(0).quantity()))
                .verifyComplete();
    }
}
