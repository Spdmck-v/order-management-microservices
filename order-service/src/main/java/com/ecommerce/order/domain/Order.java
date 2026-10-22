package com.ecommerce.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The id is assigned by the application, so a transient new flag is kept and
 * Persistable&lt;UUID&gt; tells Spring Data R2DBC to INSERT on first save
 * instead of executing an UPDATE for unknown ids.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("orders")
public class Order implements Persistable<UUID> {

    @Id
    private UUID id;

    private String customerId;

    private OrderStatus status;

    private BigDecimal totalAmount;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean newEntity;

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
