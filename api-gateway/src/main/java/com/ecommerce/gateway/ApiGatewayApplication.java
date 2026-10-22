package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway: the single public entry point. It validates JWTs (Spring
 * Security OAuth2 Resource Server), routes to order/inventory services,
 * propagates the X-Trace-Id header and reports every error in the unique
 * error format. No business logic.
 */
@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
