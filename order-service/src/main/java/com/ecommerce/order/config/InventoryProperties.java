package com.ecommerce.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(@DefaultValue("http://inventory-service:8082") String baseUrl) {
}
