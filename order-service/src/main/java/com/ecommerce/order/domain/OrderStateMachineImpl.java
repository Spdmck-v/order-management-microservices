package com.ecommerce.order.domain;

import com.ecommerce.order.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Map-backed state machine implementing the Strategy contract defined by
 * OrderStateMachine. Any transition outside the allowed map raises
 * InvalidStateTransitionException (409 INVALID_STATE_TRANSITION).
 */
@Component
public class OrderStateMachineImpl implements OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.CANCELLED)
    );

    @Override
    public OrderStatus validate(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvalidStateTransitionException(from, to);
        }
        return to;
    }
}
