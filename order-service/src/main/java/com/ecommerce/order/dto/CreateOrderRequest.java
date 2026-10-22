package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "customerId must not be blank") String customerId,
        @NotEmpty(message = "items must not be empty") @Valid List<CreateOrderItem> items
) {
}
