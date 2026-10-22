package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.ErrorResponse;
import com.ecommerce.order.dto.OrderHistoryItem;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Gestión de pedidos: creación, consulta, historial y cancelación. "
        + "Consumible únicamente a través del API Gateway con Bearer token.")
public class OrderController {

    private static final String ERROR_400_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 400,
                "code": "INVALID_REQUEST",
                "message": "items must not be empty",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private static final String ERROR_404_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 404,
                "code": "ORDER_NOT_FOUND",
                "message": "Order 7f8f4a64-6d64-43b7-8b64-8e2c5a2b1c9d not found",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private static final String ERROR_409_STOCK_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 409,
                "code": "STOCK_INSUFFICIENT",
                "message": "Inventory could not reserve stock for the requested items",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private static final String ERROR_409_STATE_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 409,
                "code": "INVALID_STATE_TRANSITION",
                "message": "Transition CANCELLED -> CANCELLED is not allowed",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private static final String ERROR_503_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 503,
                "code": "INVENTORY_UNAVAILABLE",
                "message": "Inventory service is unavailable or timed out",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un pedido", description = """
            Crea el pedido en PENDING, lo confirma (CONFIRMED) si Inventory Service reserva el stock y lo \
            marca FAILED si no. Si el stock es insuficiente responde 409 STOCK_INSUFFICIENT y si Inventory \
            Service no responde 503 INVENTORY_UNAVAILABLE.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido creado y confirmado (PENDING -> CONFIRMED con stock reservado)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = OrderResponse.class),
                            examples = @ExampleObject(name = "Pedido confirmado", value = """
                                    {
                                        "id": "0d6d5a4a-37ad-4e14-a089-64e08a8cd2b2",
                                        "customerId": "customer-001",
                                        "status": "CONFIRMED",
                                        "totalAmount": 214.30,
                                        "createdAt": "2026-10-22T12:00:00Z",
                                        "updatedAt": "2026-10-22T12:00:01Z",
                                        "items": [
                                            {
                                                "productId": "11111111-1111-4111-8b2d-111111111111",
                                                "quantity": 2,
                                                "unitPrice": 89.90
                                            },
                                            {
                                                "productId": "22222222-2222-4222-8b2d-222222222222",
                                                "quantity": 1,
                                                "unitPrice": 34.50
                                            }
                                        ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida (validación de campos, código INVALID_REQUEST)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = ERROR_400_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "Stock insuficiente en Inventory Service (código STOCK_INSUFFICIENT)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "STOCK_INSUFFICIENT", value = ERROR_409_STOCK_EXAMPLE))),
            @ApiResponse(responseCode = "503", description = "Inventory Service no disponible o timeout (código INVENTORY_UNAVAILABLE)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVENTORY_UNAVAILABLE", value = ERROR_503_EXAMPLE)))
    })
    public Mono<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un pedido por id", description = "Devuelve el pedido con su lista de items.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "El id no es un UUID válido (código INVALID_REQUEST)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = ERROR_400_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Pedido no encontrado (código ORDER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", value = ERROR_404_EXAMPLE)))
    })
    public Mono<OrderResponse> getById(@PathVariable UUID id) {
        return orderService.getById(id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Historial de estados de un pedido", description = "Devuelve todas las transiciones registradas en order_status_history, ordenadas por fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial del pedido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                                    schema = @Schema(implementation = OrderHistoryItem.class)))),
            @ApiResponse(responseCode = "400", description = "El id no es un UUID válido (código INVALID_REQUEST)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = ERROR_400_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Pedido no encontrado (código ORDER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", value = ERROR_404_EXAMPLE)))
    })
    public Mono<List<OrderHistoryItem>> getHistory(@PathVariable UUID id) {
        return orderService.getHistory(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar un pedido", description = """
            Solo permitido desde PENDING o CONFIRMED. Si estaba CONFIRMED libera el stock en Inventory \
            Service. Desde CANCELLED o FAILED responde 409 INVALID_STATE_TRANSITION.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido cancelado y stock liberado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "El id no es un UUID válido (código INVALID_REQUEST)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = ERROR_400_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Pedido no encontrado (código ORDER_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", value = ERROR_404_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "Transición de estado no permitida (código INVALID_STATE_TRANSITION)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_STATE_TRANSITION", value = ERROR_409_STATE_EXAMPLE))),
            @ApiResponse(responseCode = "503", description = "Inventory Service no disponible al liberar el stock (código INVENTORY_UNAVAILABLE)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVENTORY_UNAVAILABLE", value = ERROR_503_EXAMPLE)))
    })
    public Mono<OrderResponse> cancel(@Parameter(description = "Identificador UUID del pedido") @PathVariable UUID id) {
        return orderService.cancel(id);
    }
}
