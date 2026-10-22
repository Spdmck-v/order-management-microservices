package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class InventoryUnavailableException extends BusinessException {

    public InventoryUnavailableException() {
        super(ErrorCode.INVENTORY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                "Inventory service is unavailable or timed out");
    }
}
