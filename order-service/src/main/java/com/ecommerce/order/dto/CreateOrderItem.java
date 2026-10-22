package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Item de la solicitud de creación de pedido")
public record CreateOrderItem(
        @Schema(description = "Referencia lógica al producto en Inventory Service (sin FK entre bases de datos)",
                requiredMode = RequiredMode.REQUIRED, example = "11111111-1111-4111-8b2d-111111111111")
        @NotNull(message = "productId is required") UUID productId,
        @Schema(description = "Cantidad solicitada del producto", requiredMode = RequiredMode.REQUIRED, example = "2")
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1") Integer quantity,
        @Schema(description = "Precio unitario aplicado a la venta (> 0)", requiredMode = RequiredMode.REQUIRED, example = "89.90")
        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "unitPrice must be greater than 0") BigDecimal unitPrice
) {
}
