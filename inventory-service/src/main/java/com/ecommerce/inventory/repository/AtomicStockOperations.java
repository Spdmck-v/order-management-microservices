package com.ecommerce.inventory.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Atomic stock mutations using conditional UPDATE statements (single SQL statement,
 * no read-then-write), so concurrency correctness only depends on row locking.
 */
@Repository
@RequiredArgsConstructor
public class AtomicStockOperations {

    private static final String RESERVE = """
            UPDATE stock
            SET available_quantity = available_quantity - :qty,
                reserved_quantity  = reserved_quantity + :qty,
                version            = version + 1
            WHERE product_id = :productId
              AND available_quantity >= :qty
            """;

    private static final String RELEASE = """
            UPDATE stock
            SET available_quantity = available_quantity + :qty,
                reserved_quantity  = reserved_quantity - :qty,
                version            = version + 1
            WHERE product_id = :productId
              AND reserved_quantity >= :qty
            """;

    private final DatabaseClient databaseClient;

    public Mono<Long> reserveStock(UUID productId, int qty) {
        return execute(RESERVE, productId, qty);
    }

    public Mono<Long> releaseStock(UUID productId, int qty) {
        return execute(RELEASE, productId, qty);
    }

    private Mono<Long> execute(String sql, UUID productId, int qty) {
        return databaseClient.sql(sql)
                .bind("qty", qty)
                .bind("productId", productId)
                .fetch()
                .rowsUpdated();
    }
}
