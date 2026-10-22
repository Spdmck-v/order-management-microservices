package com.ecommerce.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that makes the X-Trace-Id propagation explicit for every
 * routed request heading to order-service / inventory-service. The request
 * headers are already decorated with the id by TraceIdWebFilter (which also
 * runs before Spring Security); this filter keeps the attribute in sync for
 * the filter chain and logs the routed request.
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getAttribute(TraceIdWebFilter.ATTRIBUTE_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceIdWebFilter.HEADER);
        }
        String finalTraceId = traceId == null || traceId.isBlank()
                ? exchange.getRequest().getId()
                : traceId;
        exchange.getAttributes().put(TraceIdWebFilter.ATTRIBUTE_KEY, finalTraceId);
        try {
            MDC.put("traceId", finalTraceId);
            log.debug("Routing {} {} with traceId {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    finalTraceId);
        } finally {
            MDC.remove("traceId");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
