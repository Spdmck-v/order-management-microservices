package com.ecommerce.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Generates or propagates the X-Trace-Id header before any other filter
 * (Spring Security included), echoes it on every response, exposes it to
 * reactive code through the Reactor context (traceId), stores it as an
 * exchange attribute for the Gateway filter chain and logs every request
 * with method, path, status, duration and trace id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String CONTEXT_KEY = "traceId";
    public static final String ATTRIBUTE_KEY = TraceIdWebFilter.class.getName() + ".TRACE_ID";

    private static final Logger log = LoggerFactory.getLogger(TraceIdWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest());
        ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> headers.set(HEADER, traceId))
                        .build())
                .build();
        mutated.getAttributes().put(ATTRIBUTE_KEY, traceId);
        mutated.getResponse().getHeaders().set(HEADER, traceId);
        long start = System.nanoTime();
        String method = mutated.getRequest().getMethod().name();
        String path = mutated.getRequest().getPath().value();
        return chain.filter(mutated)
                .contextWrite(Context.of(CONTEXT_KEY, traceId))
                .doFinally(signal -> logRequest(method, path, mutated, traceId, start));
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst(HEADER);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }

    private void logRequest(String method, String path, ServerWebExchange exchange, String traceId, long start) {
        try {
            MDC.put("traceId", traceId);
            int status = status(exchange);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} ({} ms)", method, path, status, durationMs);
        } finally {
            MDC.remove("traceId");
        }
    }

    private int status(ServerWebExchange exchange) {
        var responseStatus = exchange.getResponse().getStatusCode();
        return responseStatus != null ? responseStatus.value() : 0;
    }
}
