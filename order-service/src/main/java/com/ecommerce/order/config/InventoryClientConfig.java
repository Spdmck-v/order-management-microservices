package com.ecommerce.order.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient for the Inventory Service: baseUrl comes from
 * inventory.base-url (default http://inventory-service:8082) with 3s connect
 * and response timeouts.
 */
@Configuration
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public WebClient inventoryWebClient(InventoryProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(TIMEOUT.toMillis()))
                .responseTimeout(TIMEOUT);
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
