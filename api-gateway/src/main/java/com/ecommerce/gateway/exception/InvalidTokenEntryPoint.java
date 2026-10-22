package com.ecommerce.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Responds to absent, invalid or expired bearer tokens with the unique error
 * body and 401 INVALID_TOKEN.
 */
@Component
public class InvalidTokenEntryPoint implements ServerAuthenticationEntryPoint {

    public static final String MESSAGE = "Missing or invalid bearer token";

    private final ErrorResponseWriter errorResponseWriter;

    public InvalidTokenEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN,
                MESSAGE, true);
    }
}
