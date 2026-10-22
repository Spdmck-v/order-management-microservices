package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.Stock;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface StockRepository extends ReactiveCrudRepository<Stock, UUID> {
}
