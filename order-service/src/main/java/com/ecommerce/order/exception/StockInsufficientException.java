package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class StockInsufficientException extends BusinessException {

    public StockInsufficientException() {
        super(ErrorCode.STOCK_INSUFFICIENT, HttpStatus.CONFLICT,
                "Inventory could not reserve stock for the requested items");
    }
}
