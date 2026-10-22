package com.ecommerce.gateway.exception;

import io.netty.channel.ConnectTimeoutException;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;

/**
 * Top level error handler so unmatched routes (404 NOT_FOUND), unreachable
 * downstream services (503 SERVICE_UNAVAILABLE) and unexpected failures
 * (500 INTERNAL_ERROR) use the unique error body with X-Trace-Id.
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ErrorResponseWriter errorResponseWriter;

    public GlobalErrorWebExceptionHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        Mapping mapping = map(ex);
        return errorResponseWriter.write(exchange, mapping.status(), mapping.code(), mapping.message(), false);
    }

    private Mapping map(Throwable ex) {
        if (isConnectFailure(ex)) {
            return new Mapping(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE,
                    "Downstream service is unavailable");
        }
        if (isNotFound(ex)) {
            return new Mapping(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                    "No route or resource for the requested path");
        }
        if (ex instanceof ResponseStatusException rse) {
            int status = rse.getStatusCode().value();
            String message = rse.getReason() == null || rse.getReason().isBlank()
                    ? ex.getMessage() : rse.getReason();
            return switch (status) {
                case 404 -> new Mapping(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
                case 400 -> new Mapping(HttpStatus.BAD_REQUEST, ErrorCode.INTERNAL_ERROR, message);
                case 503 -> new Mapping(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE, message);
                default -> new Mapping(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, message);
            };
        }
        return new Mapping(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Unexpected internal error");
    }

    private boolean isNotFound(Throwable ex) {
        return ex instanceof NotFoundException
                || (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == 404);
    }

    private boolean isConnectFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof ConnectTimeoutException
                    || current instanceof UnresolvedAddressException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record Mapping(HttpStatus status, ErrorCode code, String message) {
    }
}
