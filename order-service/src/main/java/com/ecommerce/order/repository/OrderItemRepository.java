package com.ecommerce.order.repository;

import com.ecommerce.order.domain.OrderItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface OrderItemRepository extends ReactiveCrudRepository<OrderItem, Long> {

    Flux<OrderItem> findAllByOrderIdOrderByIdAsc(UUID orderId);
}
