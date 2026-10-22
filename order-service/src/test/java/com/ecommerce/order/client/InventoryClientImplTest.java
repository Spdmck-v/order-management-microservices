package com.ecommerce.order.client;

import com.ecommerce.order.dto.InventoryReservationItem;
import com.ecommerce.order.exception.InventoryUnavailableException;
import com.ecommerce.order.exception.ProductNotFoundException;
import com.ecommerce.order.exception.StockInsufficientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InventoryClientImplTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-4111-8b2d-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-4333-8b2d-333333333333");

    private final List<InventoryReservationItem> items =
            List.of(new InventoryReservationItem(PRODUCT_ID, 2));

    private InventoryClientImpl client;
    private ExchangingHandler handler;

    private static class ExchangingHandler implements ExchangeFunction {

        private HttpStatus status = HttpStatus.OK;
        private ClientRequest lastRequest;

        void respondWith(HttpStatus status) {
            this.status = status;
        }

        ClientRequest lastRequest() {
            return lastRequest;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            this.lastRequest = request;
            return Mono.just(ClientResponse.create(status).build());
        }
    }

    @BeforeEach
    void setUp() {
        handler = new ExchangingHandler();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8082")
                .exchangeFunction(handler)
                .build();
        client = new InventoryClientImpl(webClient);
    }

    @Test
    void reserveSendsBodyAndPropagatesTraceIdFromContext() {
        StepVerifier.create(
                        client.reserve(ORDER_ID, items)
                                .contextWrite(Context.of("traceId", "trace-abc")))
                .verifyComplete();

        ClientRequest request = handler.lastRequest;
        assertEquals("http://localhost:8082/api/inventory/reserve", request.url().toString());
        assertEquals("trace-abc", request.headers().getFirst("X-Trace-Id"));
    }

    @Test
    void releaseDoesNotSendTraceIdWhenContextIsEmpty() {
        StepVerifier.create(client.release(ORDER_ID, items))
                .verifyComplete();

        ClientRequest request = handler.lastRequest;
        assertEquals("http://localhost:8082/api/inventory/release", request.url().toString());
        assertFalse(request.headers().getFirst("X-Trace-Id") != null);
    }

    @Test
    void reserveTranslatesConflictToStockInsufficient() {
        handler.respondWith(HttpStatus.CONFLICT);

        StepVerifier.create(client.reserve(ORDER_ID, items))
                .expectError(StockInsufficientException.class)
                .verify();
    }

    @Test
    void reserveTranslatesNotFoundToProductNotFound() {
        handler.respondWith(HttpStatus.NOT_FOUND);

        StepVerifier.create(client.reserve(ORDER_ID, items))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void reserveTranslatesServerErrorToInventoryUnavailable() {
        handler.respondWith(HttpStatus.INTERNAL_SERVER_ERROR);

        StepVerifier.create(client.reserve(ORDER_ID, items))
                .expectError(InventoryUnavailableException.class)
                .verify();
    }

    @Test
    void releaseTranslatesConflictToStockInsufficient() {
        handler.respondWith(HttpStatus.CONFLICT);

        StepVerifier.create(client.release(ORDER_ID, items))
                .expectError(StockInsufficientException.class)
                .verify();
    }
}
