package com.ecommerce.order.mapper;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatusHistory;
import com.ecommerce.order.dto.OrderHistoryItem;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "items", source = "items")
    OrderResponse toResponse(Order order, List<OrderItem> items);

    OrderItemResponse toItemResponse(OrderItem item);

    OrderHistoryItem toHistoryItem(OrderStatusHistory history);
}
