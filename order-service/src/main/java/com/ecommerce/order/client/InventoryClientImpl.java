package com.ecommerce.order.client;

import com.ecommerce.order.config.TraceIdWebFilter;
import com.ecommerce.order.dto.InventoryReservationItem;
import com.ecommerce.order.dto.InventoryReservationRequest;
import com.ecommerce.order.exception.InventoryUnavailableException;
import com.ecommerce.order.exception.ProductNotFoundException;
import com.ecommerce.order.exception.StockInsufficientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * WebClient-backed implementation. Sends POST /api/inventory/reserve and
 * /api/inventory/release with { orderId, items:[{productId, quantity}] } and
 * propagates X-Trace-Id from the Reactor context when available.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryClientImpl implements InventoryClient {

    private static final String RESERVE_PATH = "/api/inventory/reserve";
    private static final String RELEASE_PATH = "/api/inventory/release";
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient inventoryWebClient;

    @Override
    public Mono<Void> reserve(UUID orderId, List<InventoryReservationItem> items) {
        return call(RESERVE_PATH, orderId, items);
    }

    @Override
    public Mono<Void> release(UUID orderId, List<InventoryReservationItem> items) {
        return call(RELEASE_PATH, orderId, items);
    }

    private Mono<Void> call(String path, UUID orderId, List<InventoryReservationItem> items) {
        return Mono.deferContextual(ctx -> {
            WebClient.RequestBodySpec bodySpec = inventoryWebClient.post().uri(path);
            String traceId = traceIdOf(ctx);
            if (traceId != null) {
                bodySpec.header(TraceIdWebFilter.HEADER, traceId);
            }
            return bodySpec.bodyValue(new InventoryReservationRequest(orderId, items))
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.CONFLICT,
                            response -> Mono.error(new StockInsufficientException()))
                    .onStatus(status -> status == HttpStatus.NOT_FOUND,
                            response -> Mono.error(new ProductNotFoundException()))
                    .onStatus(status -> status.is5xxServerError(),
                            response -> Mono.error(InventoryUnavailableException::new))
                    .toBodilessEntity()
                    .then()
                    .timeout(CALL_TIMEOUT, Mono.error(InventoryUnavailableException::new))
                    .onErrorResume(WebClientRequestException.class, ex -> Mono.error(unavailable(ex)))
                    .onErrorResume(TooManyRequests.class, ex -> Mono.error(unavailable(ex)))
                    .onErrorResume(WebClientResponseException.class, ex -> Mono.error(unavailable(ex)))
                    .onErrorMap(TimeoutException.class, ex -> unavailable(ex))
                    .doOnSuccess(ignored -> log.debug("Inventory call {} for order {} succeeded", path, orderId));
        });
    }

    private String traceIdOf(ContextView ctx) {
        return ctx.getOrDefault(TraceIdWebFilter.CONTEXT_KEY, null);
    }

    private InventoryUnavailableException unavailable(Throwable cause) {
        log.warn("Inventory call failed: {}", cause.getMessage());
        return new InventoryUnavailableException();
    }
}
