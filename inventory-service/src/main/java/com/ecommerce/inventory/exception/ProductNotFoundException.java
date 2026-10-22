package com.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException(UUID productId) {
        super(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Product %s not found".formatted(productId));
    }
}
