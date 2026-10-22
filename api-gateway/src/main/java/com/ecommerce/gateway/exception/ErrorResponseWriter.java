package com.ecommerce.gateway.exception;

import com.ecommerce.gateway.config.TraceIdWebFilter;
import com.ecommerce.gateway.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes every Gateway error and authentication failure using the unique
 * error body { timestamp, status, code, message, traceId }, echoing
 * X-Trace-Id on the response.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode code, String message,
                            boolean wwwAuthenticate) {
        ServerHttpResponse response = exchange.getResponse();
        String traceId = traceId(exchange);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (wwwAuthenticate) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.getHeaders().set(TraceIdWebFilter.HEADER, traceId);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(new ErrorResponse(
                    Instant.now().toString(),
                    status.value(),
                    code.name(),
                    message,
                    traceId
            ));
        } catch (Exception ignored) {
            payload = fallbackPayload(status, code, message, traceId);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }

    public String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceIdWebFilter.HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceIdWebFilter.HEADER);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    private byte[] fallbackPayload(HttpStatus status, ErrorCode code, String message, String traceId) {
        return ("{\"timestamp\":\"" + Instant.now() + "\"," +
                "\"status\":" + status.value() + "," +
                "\"code\":\"" + code.name() + "\"," +
                "\"message\":" + quote(message) + "," +
                "\"traceId\":" + quote(traceId) + "}").getBytes(StandardCharsets.UTF_8);
    }

    private String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }
}
