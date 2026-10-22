package com.ecommerce.order.dto;

import java.util.List;
import java.util.UUID;

public record InventoryReservationRequest(
        UUID orderId,
        List<InventoryReservationItem> items
) {
}
