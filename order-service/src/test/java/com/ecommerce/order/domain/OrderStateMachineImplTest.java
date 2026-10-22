package com.ecommerce.order.domain;

import com.ecommerce.order.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStateMachineImplTest {

    private final OrderStateMachine machine = new OrderStateMachineImpl();

    @Test
    void pendingToConfirmedIsAllowed() {
        assertEquals(OrderStatus.CONFIRMED, machine.validate(OrderStatus.PENDING, OrderStatus.CONFIRMED));
    }

    @Test
    void pendingToFailedIsAllowed() {
        assertEquals(OrderStatus.FAILED, machine.validate(OrderStatus.PENDING, OrderStatus.FAILED));
    }

    @Test
    void pendingToCancelledIsAllowed() {
        assertEquals(OrderStatus.CANCELLED, machine.validate(OrderStatus.PENDING, OrderStatus.CANCELLED));
    }

    @Test
    void confirmedToCancelledIsAllowed() {
        assertEquals(OrderStatus.CANCELLED, machine.validate(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
    }

    @Test
    void selfTransitionsAreRejected() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThrows(InvalidStateTransitionException.class, () -> machine.validate(status, status));
        }
    }

    @Test
    void confirmedToPendingIsRejected() {
        assertThrows(InvalidStateTransitionException.class,
                () -> machine.validate(OrderStatus.CONFIRMED, OrderStatus.PENDING));
    }

    @Test
    void confirmedToFailedIsRejected() {
        assertThrows(InvalidStateTransitionException.class,
                () -> machine.validate(OrderStatus.CONFIRMED, OrderStatus.FAILED));
    }

    @Test
    void cancelledToAnythingIsRejected() {
        for (OrderStatus to : OrderStatus.values()) {
            assertThrows(InvalidStateTransitionException.class,
                    () -> machine.validate(OrderStatus.CANCELLED, to));
        }
    }

    @Test
    void failedToAnythingIsRejected() {
        for (OrderStatus to : OrderStatus.values()) {
            assertThrows(InvalidStateTransitionException.class,
                    () -> machine.validate(OrderStatus.FAILED, to));
        }
    }

    @Test
    void invalidTransitionCarriesErrorCode() {
        InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                () -> machine.validate(OrderStatus.FAILED, OrderStatus.CONFIRMED));
        assertEquals("INVALID_STATE_TRANSITION", ex.getCode().name());
    }
}
