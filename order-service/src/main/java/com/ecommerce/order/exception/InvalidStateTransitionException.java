package com.ecommerce.order.exception;

import com.ecommerce.order.domain.OrderStatus;
import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(OrderStatus from, OrderStatus to) {
        super(ErrorCode.INVALID_STATE_TRANSITION, HttpStatus.CONFLICT,
                "Transition %s -> %s is not allowed".formatted(from, to));
    }
}
