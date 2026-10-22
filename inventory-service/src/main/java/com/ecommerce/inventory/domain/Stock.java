package com.ecommerce.inventory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("stock")
@Value
@Builder
@AllArgsConstructor
public class Stock {

    public static final Stock EMPTY = Stock.builder()
            .productId(null)
            .availableQuantity(0)
            .reservedQuantity(0)
            .version(0L)
            .build();

    @Id
    UUID productId;

    @Column("available_quantity")
    Integer availableQuantity;

    @Column("reserved_quantity")
    Integer reservedQuantity;

    Long version;
}
