package com.ecommerce.order.dto;

import com.ecommerce.order.domain.OrderStatus;

import java.time.Instant;

public record OrderHistoryItem(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String reason,
        String traceId,
        Instant changedAt
) {
}
