package com.ecommerce.order.domain;

/**
 * State/Strategy contract for order status transitions. The implementation
 * (OrderStateMachineImpl) holds the map of allowed transitions and throws
 * InvalidStateTransitionException for any other move.
 */
public interface OrderStateMachine {

    OrderStatus validate(OrderStatus from, OrderStatus to);
}
