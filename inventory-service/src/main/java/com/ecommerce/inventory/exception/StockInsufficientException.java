package com.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class StockInsufficientException extends BusinessException {

    public StockInsufficientException(UUID productId, int requested) {
        super(ErrorCode.STOCK_INSUFFICIENT, HttpStatus.CONFLICT,
                "Not enough stock for product %s: requested=%d".formatted(productId, requested));
    }
}
