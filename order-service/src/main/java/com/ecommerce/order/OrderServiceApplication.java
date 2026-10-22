package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Automatic Reactor context propagation is enabled by Spring Boot
 * (ReactorAutoConfiguration); the trace id stored in the reactive context by
 * TraceIdWebFilter is restored into the MDC on every signal thread through
 * TraceIdMdcAccessor, registered via ServiceLoader.
 */
@SpringBootApplication
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
