package com.ecommerce.order.domain;

/**
 * Lifecycle of an order. Allowed transitions (OrderStateMachine):
 * PENDING -> CONFIRMED, PENDING -> FAILED, PENDING -> CANCELLED, CONFIRMED -> CANCELLED.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
