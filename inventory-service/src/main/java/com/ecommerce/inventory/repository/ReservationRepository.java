package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReservationRepository {

    private static final String FIND_BY_KEY = """
            SELECT order_id, product_id, quantity, status
            FROM stock_reservations
            WHERE order_id = :orderId AND product_id = :productId
            """;

    private static final String UPSERT_RESERVED = """
            INSERT INTO stock_reservations (order_id, product_id, quantity, status)
            VALUES (:orderId, :productId, :quantity, 'RESERVED')
            ON CONFLICT (order_id, product_id)
            DO UPDATE SET quantity = EXCLUDED.quantity, status = 'RESERVED'
            """;

    private static final String MARK_RELEASED = """
            UPDATE stock_reservations
            SET status = 'RELEASED'
            WHERE order_id = :orderId AND product_id = :productId AND status = 'RESERVED'
            """;

    private final DatabaseClient databaseClient;

    public Mono<StockReservation> find(UUID orderId, UUID productId) {
        return databaseClient.sql(FIND_BY_KEY)
                .bind("orderId", orderId)
                .bind("productId", productId)
                .map((row, meta) -> new StockReservation(
                        row.get("order_id", UUID.class),
                        row.get("product_id", UUID.class),
                        row.get("quantity", Integer.class),
                        ReservationStatus.valueOf(row.get("status", String.class))))
                .first();
    }

    public Mono<Long> upsertReserved(UUID orderId, UUID productId, int quantity) {
        return databaseClient.sql(UPSERT_RESERVED)
                .bind("orderId", orderId)
                .bind("productId", productId)
                .bind("quantity", quantity)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> markReleased(UUID orderId, UUID productId) {
        return databaseClient.sql(MARK_RELEASED)
                .bind("orderId", orderId)
                .bind("productId", productId)
                .fetch()
                .rowsUpdated();
    }
}
