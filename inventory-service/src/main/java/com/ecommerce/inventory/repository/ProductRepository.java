package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.Product;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;

import java.util.UUID;

public interface ProductRepository extends ReactiveCrudRepository<Product, UUID>, ReactiveSortingRepository<Product, UUID> {
}
