package com.ecommerce.inventory.mapper;

import com.ecommerce.inventory.domain.Product;
import com.ecommerce.inventory.domain.Stock;
import com.ecommerce.inventory.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "availableQuantity", source = "stock.availableQuantity")
    @Mapping(target = "reservedQuantity", source = "stock.reservedQuantity")
    ProductResponse toResponse(Product product, Stock stock);
}
