package com.ecommerce.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Responds to authenticated callers without the required authority with the
 * unique error body and 403 FORBIDDEN.
 */
@Component
public class ForbiddenAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    public ForbiddenAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return errorResponseWriter.write(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "Access to the requested resource is denied", false);
    }
}
