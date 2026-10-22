package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException() {
        super(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "One of the requested products does not exist in inventory");
    }
}
