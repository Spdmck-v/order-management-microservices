package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
    }
}
