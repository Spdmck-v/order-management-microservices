package com.ecommerce.inventory.service;

import com.ecommerce.inventory.domain.Product;
import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.Stock;
import com.ecommerce.inventory.dto.OperationItemResponse;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReleaseItemRequest;
import com.ecommerce.inventory.dto.ReleaseRequest;
import com.ecommerce.inventory.dto.ReserveItemRequest;
import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.dto.StockOperationResponse;
import com.ecommerce.inventory.exception.InvalidRequestException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import com.ecommerce.inventory.exception.StockInsufficientException;
import com.ecommerce.inventory.mapper.ProductMapper;
import com.ecommerce.inventory.repository.AtomicStockOperations;
import com.ecommerce.inventory.repository.ProductRepository;
import com.ecommerce.inventory.repository.ReservationRepository;
import com.ecommerce.inventory.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final String ITEM_RESERVED = "RESERVED";
    private static final String ITEM_ALREADY_RESERVED = "ALREADY_RESERVED";
    private static final String ITEM_RELEASED = "RELEASED";
    private static final String ITEM_NOT_RESERVED = "NOT_RESERVED";

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final AtomicStockOperations atomicStockOperations;
    private final ProductMapper productMapper;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<List<ProductResponse>> listProducts() {
        return stockRepository.findAll()
                .collectMap(Stock::getProductId)
                .flatMap(stockByProduct -> productRepository.findAll(Sort.by(Sort.Direction.ASC, "sku"))
                        .map(product -> productMapper.toResponse(product,
                                stockByProduct.getOrDefault(product.getId(), Stock.EMPTY)))
                        .collectSortedList());
    }

    @Override
    public Mono<ProductResponse> getProduct(UUID productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(productId)))
                .flatMap(product -> stockRepository.findById(product.getId())
                        .defaultIfEmpty(Stock.EMPTY)
                        .map(stock -> productMapper.toResponse(product, stock)));
    }

    @Override
    public Mono<StockOperationResponse> reserve(ReserveRequest request) {
        UUID orderId = requireOrderId(request.orderId());
        List<ReserveItemRequest> items = mergeItems(request.items());

        return Flux.fromIterable(items)
                .concatMap(item -> reserveItem(orderId, item))
                .collectList()
                .map(reservations -> new StockOperationResponse(orderId, reservations))
                .as(transactionalOperator::transactional)
                .doOnSuccess(response -> log.info("Stock reserved for orderId={} items={}", orderId, response.items()));
    }

    @Override
    public Mono<StockOperationResponse> release(ReleaseRequest request) {
        UUID orderId = requireOrderId(request.orderId());
        List<ReleaseItemRequest> items = mergeReleaseItems(request.items());

        return Flux.fromIterable(items)
                .concatMap(item -> releaseItem(orderId, item))
                .collectList()
                .map(operations -> new StockOperationResponse(orderId, operations))
                .as(transactionalOperator::transactional)
                .doOnSuccess(response -> log.info("Stock released for orderId={} items={}", orderId, response.items()));
    }

    private Mono<OperationItemResponse> reserveItem(UUID orderId, ReserveItemRequest item) {
        return productRepository.findById(item.productId())
                .switchIfEmpty(Mono.error(new ProductNotFoundException(item.productId())))
                .flatMap(product -> {
                    if (!product.isActive()) {
                        return Mono.error(new InvalidRequestException(
                                "Product %s is not active".formatted(item.productId())));
                    }
                    return reservationRepository.find(orderId, item.productId())
                            .filter(reservation -> reservation.status() == ReservationStatus.RESERVED)
                            .map(reservation -> new OperationItemResponse(
                                    item.productId(), reservation.quantity(), ITEM_ALREADY_RESERVED))
                            .switchIfEmpty(Mono.defer(() -> doReserve(orderId, item)));
                });
    }

    private Mono<OperationItemResponse> doReserve(UUID orderId, ReserveItemRequest item) {
        return atomicStockOperations.reserveStock(item.productId(), item.quantity())
                .flatMap(rows -> {
                    if (rows == 0) {
                        return Mono.error(new StockInsufficientException(item.productId(), item.quantity()));
                    }
                    return reservationRepository.upsertReserved(orderId, item.productId(), item.quantity())
                            .then(Mono.just(new OperationItemResponse(
                                    item.productId(), item.quantity(), ITEM_RESERVED)));
                });
    }

    private Mono<OperationItemResponse> releaseItem(UUID orderId, ReleaseItemRequest item) {
        return reservationRepository.find(orderId, item.productId())
                .filter(reservation -> reservation.status() == ReservationStatus.RESERVED)
                .flatMap(reservation -> atomicStockOperations.releaseStock(item.productId(), reservation.quantity())
                        .flatMap(rows -> {
                            if (rows == 0) {
                                return Mono.error(new InvalidRequestException(
                                        "Corrupt stock state for product %s".formatted(item.productId())));
                            }
                            return reservationRepository.markReleased(orderId, item.productId())
                                    .then(Mono.just(new OperationItemResponse(
                                            item.productId(), reservation.quantity(), ITEM_RELEASED)));
                        }))
                .switchIfEmpty(Mono.defer(() -> Mono.just(new OperationItemResponse(
                        item.productId(), item.quantity(), ITEM_NOT_RESERVED))));
    }

    private UUID requireOrderId(UUID orderId) {
        if (orderId == null) {
            throw new InvalidRequestException("orderId is required");
        }
        return orderId;
    }

    private List<ReserveItemRequest> mergeItems(List<ReserveItemRequest> items) {
        Map<UUID, Integer> merged = new LinkedHashMap<>();
        for (ReserveItemRequest item : items) {
            if (item.productId() == null) {
                throw new InvalidRequestException("productId is required");
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new InvalidRequestException("quantity must be at least 1 for product %s"
                        .formatted(item.productId()));
            }
            merged.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return merged.entrySet().stream()
                .map(entry -> new ReserveItemRequest(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ReleaseItemRequest> mergeReleaseItems(List<ReleaseItemRequest> items) {
        Map<UUID, Integer> merged = new LinkedHashMap<>();
        for (ReleaseItemRequest item : items) {
            if (item.productId() == null) {
                throw new InvalidRequestException("productId is required");
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new InvalidRequestException("quantity must be at least 1 for product %s"
                        .formatted(item.productId()));
            }
            merged.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return merged.entrySet().stream()
                .map(entry -> new ReleaseItemRequest(entry.getKey(), entry.getValue()))
                .toList();
    }
}
