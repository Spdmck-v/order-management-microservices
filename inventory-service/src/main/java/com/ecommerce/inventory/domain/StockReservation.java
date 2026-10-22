package com.ecommerce.inventory.domain;

import java.util.UUID;

public record StockReservation(
        UUID orderId,
        UUID productId,
        Integer quantity,
        ReservationStatus status
) {
}
