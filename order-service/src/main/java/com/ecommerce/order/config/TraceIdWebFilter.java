package com.ecommerce.order.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Generates or propagates the X-Trace-Id header, echoes it on every response
 * (including error responses) and exposes it to reactive code through the
 * Reactor context (traceId). With automatic context propagation enabled, the
 * id also lands in the MDC on every signal thread (TraceIdMdcAccessor).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String CONTEXT_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().set(HEADER, traceId);
        String finalTraceId = traceId;
        return chain.filter(exchange)
                .contextWrite(Context.of(CONTEXT_KEY, finalTraceId));
    }
}
