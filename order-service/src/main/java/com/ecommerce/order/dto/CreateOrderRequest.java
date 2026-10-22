package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Solicitud de creación de un pedido")
public record CreateOrderRequest(
        @Schema(description = "Identificador del cliente que compra", requiredMode = RequiredMode.REQUIRED, example = "customer-001")
        @NotBlank(message = "customerId must not be blank") String customerId,
        @Schema(description = "Items del pedido (al menos uno)", requiredMode = RequiredMode.REQUIRED)
        @NotEmpty(message = "items must not be empty") @Valid List<CreateOrderItem> items
) {
}
