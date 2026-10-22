package com.ecommerce.order.repository;

import com.ecommerce.order.domain.OrderStatusHistory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface OrderStatusHistoryRepository extends ReactiveCrudRepository<OrderStatusHistory, Long> {

    Flux<OrderStatusHistory> findAllByOrderIdOrderByChangedAtAsc(UUID orderId);
}
