package com.ecommerce.order.client;

import com.ecommerce.order.dto.InventoryReservationItem;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Reactive contract to talk to the Inventory Service. Implementations must
 * propagate X-Trace-Id from the Reactor context (when present) and translate
 * Inventory failures into the business exceptions of this service.
 */
public interface InventoryClient {

    Mono<Void> reserve(UUID orderId, List<InventoryReservationItem> items);

    Mono<Void> release(UUID orderId, List<InventoryReservationItem> items);
}
