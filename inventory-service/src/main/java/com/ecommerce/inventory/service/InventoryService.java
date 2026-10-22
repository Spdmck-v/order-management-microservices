package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReleaseRequest;
import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.dto.StockOperationResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    Mono<List<ProductResponse>> listProducts();

    Mono<ProductResponse> getProduct(UUID productId);

    Mono<StockOperationResponse> reserve(ReserveRequest request);

    Mono<StockOperationResponse> release(ReleaseRequest request);
}
