package com.ecommerce.order.exception;

import com.ecommerce.order.config.TraceIdWebFilter;
import com.ecommerce.order.dto.CreateOrderItem;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies the unified error format (timestamp, status, code, message,
 * traceId), X-Trace-Id echoing (provided and generated) and request validation
 * through the whole reactive stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceIdErrorHandlingTest {

    private static final Pattern GENERATED_TRACE_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @LocalServerPort
    private int port;

    @MockitoBean
    private OrderService orderService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void orderNotFoundReturnsUnifiedErrorBodyWithGeneratedTraceId() {
        UUID id = UUID.randomUUID();
        when(orderService.getById(id)).thenReturn(Mono.error(new OrderNotFoundException(id)));

        var result = client.get()
                .uri("/api/orders/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Trace-Id")
                .expectBody(ErrorResponseAssert.class)
                .returnResult()
                .getResponseBody();

        assertGeneratedTraceErrorBody(result, 404, "ORDER_NOT_FOUND");
    }

    @Test
    void providedTraceIdIsEchoedOnNotFoundError() {
        UUID id = UUID.randomUUID();
        String traceId = "trace-abc-123";
        when(orderService.getById(id)).thenReturn(Mono.error(new OrderNotFoundException(id)));

        var result = client.get()
                .uri("/api/orders/{id}", id)
                .header(TraceIdWebFilter.HEADER, traceId)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals(TraceIdWebFilter.HEADER, traceId)
                .expectBody(ErrorResponseAssert.class)
                .returnResult()
                .getResponseBody();

        assertEquals(traceId, result.traceId());
        assertEquals(404, result.status());
        assertEquals("ORDER_NOT_FOUND", result.code());
        assertNotNull(result.timestamp());
    }

    @Test
    void successfulResponseEchoesGeneratedTraceIdEvenWithoutIncomingHeader() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Trace-Id", value -> {
                    assertNotNull(value);
                    assertTrue(GENERATED_TRACE_ID.matcher(value).matches(),
                            "generated trace id should be a UUID but was: " + value);
                });
    }

    @Test
    void oversizedTraceIdIsIgnoredAndReplacedWithGeneratedUuid() {
        String oversizedTraceId = "a".repeat(100);

        client.get()
                .uri("/v3/api-docs")
                .header(TraceIdWebFilter.HEADER, oversizedTraceId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Trace-Id", value -> {
                    assertNotNull(value);
                    assertTrue(GENERATED_TRACE_ID.matcher(value).matches(),
                            "oversized incoming trace id should be ignored and replaced with a UUID but was: " + value);
                });
    }

    @Test
    void emptyItemsReturnBadRequestWithDetails() {
        when(orderService.create(any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/orders")
                .header(TraceIdWebFilter.HEADER, "validation-trace")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateOrderRequest("customer-1", List.of()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(TraceIdWebFilter.HEADER, "validation-trace")
                .expectBody(ErrorResponseAssert.class)
                .value(body -> {
                    assertEquals(400, body.status());
                    assertEquals("INVALID_REQUEST", body.code());
                    assertEquals("validation-trace", body.traceId());
                    assertTrue(body.message().contains("items"));
                });
    }

    @Test
    void invalidItemFieldsReturnBadRequest() {
        when(orderService.create(any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/orders")
                .header(TraceIdWebFilter.HEADER, "item-trace")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateOrderRequest("customer-1",
                        List.of(new CreateOrderItem(UUID.randomUUID(), 0, BigDecimal.ZERO))))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(TraceIdWebFilter.HEADER, "item-trace")
                .expectBody(ErrorResponseAssert.class)
                .value(body -> {
                    assertEquals(400, body.status());
                    assertEquals("INVALID_REQUEST", body.code());
                    assertEquals("item-trace", body.traceId());
                });
    }

    @Test
    void malformedJsonReturnsInvalidRequest() {
        when(orderService.create(any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/orders")
                .header(TraceIdWebFilter.HEADER, "bad-json-trace")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not-json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(TraceIdWebFilter.HEADER, "bad-json-trace")
                .expectBody(ErrorResponseAssert.class)
                .value(body -> {
                    assertEquals(400, body.status());
                    assertEquals("INVALID_REQUEST", body.code());
                    assertEquals("bad-json-trace", body.traceId());
                });
    }

    private void assertGeneratedTraceErrorBody(ErrorResponseAssert body, int status, String code) {
        assertNotNull(body);
        assertNotNull(body.traceId());
        assertTrue(GENERATED_TRACE_ID.matcher(body.traceId).matches(),
                "generated trace id should be a UUID but was: " + body.traceId);
        assertEquals(status, body.status());
        assertEquals(code, body.code());
        assertNotNull(body.message());
        assertNotNull(body.timestamp());
    }

    /**
     * Raw mirror of the error body used for assertions, without Jackson type coupling.
     */
    record ErrorResponseAssert(Instant timestamp, int status, String code, String message, String traceId) {
    }
}
