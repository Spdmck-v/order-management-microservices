package com.ecommerce.inventory.exception;

import com.ecommerce.inventory.config.TraceIdWebFilter;
import com.ecommerce.inventory.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.stream.Collectors;

/**
 * Global error handling. Every error uses the common error body
 * { timestamp, status, code, message, traceId } and echoes X-Trace-Id.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidation(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> "%s: %s".formatted(fe.getField(), fe.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusiness(BusinessException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidInput(ServerWebInputException ex) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                ex.getReason() == null ? "Invalid request body" : ex.getReason());
    }

    @ExceptionHandler({ConversionFailedException.class, MethodArgumentTypeMismatchException.class})
    public Mono<ResponseEntity<ErrorResponse>> handleTypeMismatch(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                "Invalid request parameter: %s".formatted(ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status.is2xxSuccessful()) {
            // Should never be thrown as an error; keep semantics of the code.
            return buildInternal("Unexpected response status exception", ex);
        }
        ErrorCode code = switch (status) {
            case BAD_REQUEST -> ErrorCode.INVALID_REQUEST;
            case UNAUTHORIZED -> ErrorCode.INVALID_TOKEN;
            case NOT_FOUND -> ErrorCode.PRODUCT_NOT_FOUND;
            default -> ErrorCode.INTERNAL_ERROR;
        };
        return build(status, code, ex.getReason() == null ? ex.getMessage() : ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Unexpected internal error");
    }

    private Mono<ResponseEntity<ErrorResponse>> build(HttpStatus status, ErrorCode code, String message) {
        return Mono.deferContextual(ctx -> Mono.just(construct(status, code, message, ctx)));
    }

    private Mono<ResponseEntity<ErrorResponse>> buildInternal(String message, Exception cause) {
        log.error("Unhandled exception", cause);
        return Mono.deferContextual(ctx ->
                Mono.just(construct(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, message, ctx)));
    }

    private ResponseEntity<ErrorResponse> construct(HttpStatus status, ErrorCode code, String message, ContextView ctx) {
        String traceId = ctx.getOrDefault(TraceIdWebFilter.CONTEXT_KEY, "NO-TRACE");
        log.warn("[{}] {} -> {} {}", traceId, code, status.value(), message);
        return ResponseEntity.status(status).body(ErrorResponse.of(status, code.name(), message, traceId));
    }
}
