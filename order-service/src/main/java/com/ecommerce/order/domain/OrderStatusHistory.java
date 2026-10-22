package com.ecommerce.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order_status_history")
public class OrderStatusHistory {

    @Id
    private Long id;

    private UUID orderId;

    private OrderStatus fromStatus;

    private OrderStatus toStatus;

    private String reason;

    private String traceId;

    private Instant changedAt;
}
