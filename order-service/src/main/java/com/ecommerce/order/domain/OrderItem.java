package com.ecommerce.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * product_id is a logical reference to the inventory database; there is no
 * foreign key between databases.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order_items")
public class OrderItem {

    @Id
    private Long id;

    private UUID orderId;

    private UUID productId;

    private Integer quantity;

    private BigDecimal unitPrice;
}
