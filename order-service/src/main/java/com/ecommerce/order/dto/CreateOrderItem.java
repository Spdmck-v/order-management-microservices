package com.ecommerce.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderItem(
        @NotNull(message = "productId is required") UUID productId,
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1") Integer quantity,
        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "unitPrice must be greater than 0") BigDecimal unitPrice
) {
}
