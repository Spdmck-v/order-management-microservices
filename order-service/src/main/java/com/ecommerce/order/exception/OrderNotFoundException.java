package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(java.util.UUID orderId) {
        super(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Order %s not found".formatted(orderId));
    }
}
