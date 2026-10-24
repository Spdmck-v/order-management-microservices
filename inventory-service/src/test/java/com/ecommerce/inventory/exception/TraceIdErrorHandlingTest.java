package com.ecommerce.inventory.exception;

import com.ecommerce.inventory.config.TraceIdWebFilter;
import com.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private InventoryService inventoryService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void productNotFoundReturnsUnifiedErrorBodyWithGeneratedTraceId() {
        UUID id = UUID.randomUUID();
        when(inventoryService.getProduct(id)).thenReturn(Mono.error(new ProductNotFoundException(id)));

        var result = client.get()
                .uri("/api/inventory/products/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Trace-Id")
                .expectBody(ErrorResponseAssert.class)
                .returnResult()
                .getResponseBody();

        assertErrorBody(result, 404, "PRODUCT_NOT_FOUND");
    }

    @Test
    void providedTraceIdIsEchoedOnResponseAndErrorBody() {
        UUID id = UUID.randomUUID();
        String traceId = "trace-abc-123";
        when(inventoryService.getProduct(id)).thenReturn(Mono.error(new ProductNotFoundException(id)));

        var result = client.get()
                .uri("/api/inventory/products/{id}", id)
                .header(TraceIdWebFilter.HEADER, traceId)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals(TraceIdWebFilter.HEADER, traceId)
                .expectBody(ErrorResponseAssert.class)
                .returnResult()
                .getResponseBody();

        assertErrorBody(result, 404, "PRODUCT_NOT_FOUND", traceId);
    }

    @Test
    void successfulResponseEchoesTraceIdEvenWithoutIncomingHeader() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Trace-Id", value -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(value);
                    org.junit.jupiter.api.Assertions.assertTrue(GENERATED_TRACE_ID.matcher(value).matches(),
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
                    org.junit.jupiter.api.Assertions.assertNotNull(value);
                    org.junit.jupiter.api.Assertions.assertTrue(GENERATED_TRACE_ID.matcher(value).matches(),
                            "oversized incoming trace id should be ignored and replaced with a UUID but was: " + value);
                });
    }

    @Test
    void invalidReserveBodyReturnsBadRequestWithValidationDetails() {
        when(inventoryService.reserve(any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/inventory/reserve")
                .header(TraceIdWebFilter.HEADER, "validation-trace")
                .header("Content-Type", "application/json")
                .bodyValue("{\"items\": []}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(TraceIdWebFilter.HEADER, "validation-trace")
                .expectBody(ErrorResponseAssert.class)
                .value(body -> assertErrorBody(body, 400, "INVALID_REQUEST", "validation-trace"));
    }

    @Test
    void malformedJsonReturnsInvalidRequest() {
        when(inventoryService.reserve(any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/inventory/reserve")
                .header(TraceIdWebFilter.HEADER, "bad-json-trace")
                .header("Content-Type", "application/json")
                .bodyValue("{not-json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(TraceIdWebFilter.HEADER, "bad-json-trace")
                .expectBody(ErrorResponseAssert.class)
                .value(body -> assertErrorBody(body, 400, "INVALID_REQUEST", "bad-json-trace"));
    }

    private void assertErrorBody(ErrorResponseAssert body, int status, String code) {
        org.junit.jupiter.api.Assertions.assertNotNull(body);
        org.junit.jupiter.api.Assertions.assertNotNull(body.traceId);
        org.junit.jupiter.api.Assertions.assertTrue(GENERATED_TRACE_ID.matcher(body.traceId).matches(),
                "generated trace id should be a UUID but was: " + body.traceId);
        org.junit.jupiter.api.Assertions.assertEquals(status, body.status);
        org.junit.jupiter.api.Assertions.assertEquals(code, body.code);
        org.junit.jupiter.api.Assertions.assertNotNull(body.message);
        org.junit.jupiter.api.Assertions.assertNotNull(body.timestamp);
    }

    private void assertErrorBody(ErrorResponseAssert body, int status, String code, String traceId) {
        org.junit.jupiter.api.Assertions.assertNotNull(body);
        org.junit.jupiter.api.Assertions.assertEquals(traceId, body.traceId);
        org.junit.jupiter.api.Assertions.assertEquals(status, body.status);
        org.junit.jupiter.api.Assertions.assertEquals(code, body.code);
        org.junit.jupiter.api.Assertions.assertNotNull(body.message);
        org.junit.jupiter.api.Assertions.assertNotNull(body.timestamp);
    }

    /**
     * Raw mirror of the error body used for assertions, without Jackson type coupling.
     */
    record ErrorResponseAssert(Instant timestamp, int status, String code, String message, String traceId) {
    }

    private static final class TraceIdConstants {
        private static final String HEADER = "X-Trace-Id";
    }
}
