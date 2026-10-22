package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.ErrorResponse;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReleaseRequest;
import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.dto.StockOperationResponse;
import com.ecommerce.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Catálogo de productos, reserva y liberación de stock. "
        + "Consumible únicamente a través del API Gateway con Bearer token.")
public class InventoryController {

    private static final String ERROR_400_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 400,
                "code": "INVALID_REQUEST",
                "message": "quantity must be at least 1 for product 11111111-1111-4111-8b2d-111111111111",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private static final String ERROR_404_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 404,
                "code": "PRODUCT_NOT_FOUND",
                "message": "Product 11111111-1111-4111-8b2d-111111111111 not found",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private static final String ERROR_409_EXAMPLE = """
            {
                "timestamp": "2026-10-22T12:00:00Z",
                "status": 409,
                "code": "STOCK_INSUFFICIENT",
                "message": "Not enough stock for product 55555555-5555-4555-8b2d-555555555555: requested=100",
                "traceId": "3f2c7c1e-103c-4e35-9f63-0b1ba2f99c8f"
            }
            """;

    private final InventoryService inventoryService;

    @GetMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar productos con stock", description = "Devuelve el catálogo de productos activos con su stock disponible y reservado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo de productos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class)),
                            examples = @ExampleObject(name = "Catálogo semilla", value = """
                                    [
                                        {
                                            "id": "11111111-1111-4111-8b2d-111111111111",
                                            "sku": "SKU-001",
                                            "name": "Mechanical Keyboard",
                                            "price": 89.90,
                                            "active": true,
                                            "availableQuantity": 100,
                                            "reservedQuantity": 0
                                        },
                                        {
                                            "id": "22222222-2222-4222-8b2d-222222222222",
                                            "sku": "SKU-002",
                                            "name": "Wireless Mouse",
                                            "price": 34.50,
                                            "active": true,
                                            "availableQuantity": 100,
                                            "reservedQuantity": 0
                                        }
                                    ]
                                    """)))
    })
    public Mono<List<ProductResponse>> listProducts() {
        return inventoryService.listProducts();
    }

    @GetMapping(value = "/products/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar un producto por id", description = "Devuelve el producto con su stock disponible y reservado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductResponse.class),
                            examples = @ExampleObject(name = "Producto", value = """
                                    {
                                        "id": "11111111-1111-4111-8b2d-111111111111",
                                        "sku": "SKU-001",
                                        "name": "Mechanical Keyboard",
                                        "price": 89.90,
                                        "active": true,
                                        "availableQuantity": 100,
                                        "reservedQuantity": 0
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "El id no es un UUID válido (código INVALID_REQUEST)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = ERROR_400_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado (código PRODUCT_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "PRODUCT_NOT_FOUND", value = ERROR_404_EXAMPLE)))
    })
    public Mono<ProductResponse> getProduct(@Parameter(description = "Identificador UUID del producto") @PathVariable("id") UUID id) {
        return inventoryService.getProduct(id);
    }

    @PostMapping(value = "/reserve", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reservar stock", description = """
            Descuenta stock de forma ATÓMICA (UPDATE condicional en SQL) y registra la reserva por \
            orderId para idempotencia. Si el producto ya tiene reserva para ese orderId responde \
            ALREADY_RESERVED. Artículos inactivos fallan con 400 y stock insuficiente con 409.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva realizada (o idempotente, ALREADY_RESERVED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StockOperationResponse.class),
                            examples = @ExampleObject(name = "Reserva confirmada", value = """
                                    {
                                        "orderId": "f96c3ab4-3f6a-4b3e-a2a0-3dfb1d914a93",
                                        "items": [
                                            {
                                                "productId": "11111111-1111-4111-8b2d-111111111111",
                                                "quantity": 2,
                                                "status": "RESERVED"
                                            }
                                        ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida o producto inactivo (código INVALID_REQUEST)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = ERROR_400_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado (código PRODUCT_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "PRODUCT_NOT_FOUND", value = ERROR_404_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "Stock insuficiente (código STOCK_INSUFFICIENT)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "STOCK_INSUFFICIENT", value = ERROR_409_EXAMPLE)))
    })
    public Mono<StockOperationResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping(value = "/release", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Liberar stock reservado", description = """
            Devuelve al stock disponible la cantidad reservada por orderId y marca la reserva como \
            RELEASED. Si no existe reserva para un artículo responde NOT_RESERVED (sin error).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liberación realizada (o NOT_RESERVED si no había reserva)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StockOperationResponse.class),
                            examples = @ExampleObject(name = "Liberación confirmada", value = """
                                    {
                                        "orderId": "f96c3ab4-3f6a-4b3e-a2a0-3dfb1d914a93",
                                        "items": [
                                            {
                                                "productId": "11111111-1111-4111-8b2d-111111111111",
                                                "quantity": 2,
                                                "status": "RELEASED"
                                            }
                                        ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida (código INVALID_REQUEST)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = ERROR_400_EXAMPLE)))
    })
    public Mono<StockOperationResponse> release(@Valid @RequestBody ReleaseRequest request) {
        return inventoryService.release(request);
    }
}
